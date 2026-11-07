package com.kodika.kodikalab.teams.groupmembership;

import com.kodika.kodikalab.common.exception.BadRequestException;
import com.kodika.kodikalab.common.exception.ConflictException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.profiles.practitioner.PractitionerProfile;
import com.kodika.kodikalab.profiles.practitioner.PractitionerProfileService;
import com.kodika.kodikalab.teams.groupmembership.dto.GroupMemberData;
import com.kodika.kodikalab.teams.studygroup.GroupStatus;
import com.kodika.kodikalab.teams.studygroup.GroupVisibility;
import com.kodika.kodikalab.teams.studygroup.StudyGroup;
import com.kodika.kodikalab.teams.studygroup.StudyGroupRepository;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupMembershipServiceImpl implements GroupMembershipService {

    private final GroupMembershipRepository membershipRepository;
    private final StudyGroupRepository groupRepository;
    private final PractitionerProfileService practitionerProfileService;
    private final CurrentUserResolver currentUserResolver;
    private final InvitationCodeAttemptLimiter codeAttemptLimiter;

    public GroupMembershipServiceImpl(
            GroupMembershipRepository membershipRepository,
            StudyGroupRepository groupRepository,
            PractitionerProfileService practitionerProfileService,
            CurrentUserResolver currentUserResolver,
            InvitationCodeAttemptLimiter codeAttemptLimiter) {
        this.membershipRepository = membershipRepository;
        this.groupRepository = groupRepository;
        this.practitionerProfileService = practitionerProfileService;
        this.currentUserResolver = currentUserResolver;
        this.codeAttemptLimiter = codeAttemptLimiter;
    }

    @Override
    @Transactional
    public GroupMembership requestJoin(Integer groupId, String invitationCode) {
        return join(
                () -> groupRepository.findById(groupId)
                        .orElseThrow(() -> new NotFoundException(
                                "El grupo solicitado no existe"
                        )),
                invitationCode,
                false
        );
    }

    @Override
    @Transactional
    public GroupMembership joinByCode(String invitationCode) {
        String code = normalizeInvitationCode(invitationCode);

        return join(
                () -> findGroupByCode(code),
                code,
                true
        );
    }

    /** Cada código inexistente consume un intento del practicante; uno válido lo devuelve. */
    private StudyGroup findGroupByCode(String code) {
        Integer userId = currentUserResolver.currentUser().getId();
        Instant window = codeAttemptLimiter.reserveAttempt(userId);
        StudyGroup group = groupRepository.findByInvitationCode(code)
                .orElseThrow(() -> new NotFoundException(
                        "El código de invitación no es válido"
                ));
        codeAttemptLimiter.refundAttempt(userId, window);
        return group;
    }

    /**
     * Flujo de ingreso compartido por {@link #requestJoin} y {@link #joinByCode}: valida la cuenta,
     * el estado del grupo, duplicados, expulsiones y cupo. Con {@code codeProven} el código ya
     * identificó al grupo, así que el ingreso es directo aunque sea PROTEGIDO.
     */
    private GroupMembership join(
            Supplier<StudyGroup> groupLookup,
            String invitationCode,
            boolean codeProven) {
        User user = currentUserResolver.currentUser();

        if (user.getRole() != Role.PRACTICANTE) {
            throw new ForbiddenException(
                    "Solo los practicantes pueden solicitar ingreso"
            );
        }

        PractitionerProfile practitioner = practitionerProfileService
                .requirePractitionerProfile(user.getId());

        StudyGroup group = groupLookup.get();
        Integer groupId = group.getId();

        if (group.getStatus() == GroupStatus.BORRADOR) {
            throw new ConflictException(
                    "El grupo todavía no está publicado"
            );
        }

        if (group.getStatus() != GroupStatus.ACTIVO) {
            throw new ConflictException(
                    "No se puede ingresar a un grupo archivado"
            );
        }

        boolean directJoin = codeProven
                || group.getVisibility() == GroupVisibility.PUBLICO
                || (invitationCode != null
                && invitationCode.equals(group.getInvitationCode()));

        GroupMembership membership = membershipRepository
                .findByGroupIdAndPractitionerUserId(groupId, user.getId())
                .orElseGet(GroupMembership::new);

        if (membership.getStatus() == MembershipStatus.ACTIVO) {
            throw new ConflictException("Ya perteneces a este grupo");
        }

        if (membership.getStatus() == MembershipStatus.PENDIENTE
                || membership.getStatus() == MembershipStatus.ACLARACION_SOLICITADA) {
            throw new ConflictException(
                    "Ya tienes una solicitud pendiente para este grupo"
            );
        }

        if (membership.getStatus() == MembershipStatus.EXPULSADO) {
            throw new ForbiddenException(
                    "Un practicante expulsado no puede volver a ingresar"
            );
        }

        ensureCapacity(group);

        membership.setGroup(group);
        membership.setPractitioner(practitioner);
        membership.setTeamRole(TeamRole.MIEMBRO);
        membership.setLeftAt(null);
        membership.setJoinedAt(OffsetDateTime.now());
        clearClarification(membership);
        membership.setStatus(
                directJoin ? MembershipStatus.ACTIVO : MembershipStatus.PENDIENTE
        );

        return membershipRepository.save(membership);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupMembership> getRequests(Integer groupId, MembershipStatus status) {
        User user = currentUserResolver.currentUser();

        if (user.getRole() != Role.COACH) {
            throw new ForbiddenException(
                    "Solo los coaches pueden consultar solicitudes"
            );
        }

        if (status != MembershipStatus.PENDIENTE
                && status != MembershipStatus.ACLARACION_SOLICITADA
                && status != MembershipStatus.ACTIVO
                && status != MembershipStatus.EXPULSADO) {
            throw new BadRequestException(
                    "Solo se permite consultar miembros ACTIVO, EXPULSADO, solicitudes PENDIENTE o ACLARACION_SOLICITADA"
            );
        }

        StudyGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException(
                        "El grupo no existe"
                ));

        ensureResponsibleCoach(group, user);

        return membershipRepository.findByGroupIdAndStatus(groupId, status);
    }

    @Override
    @Transactional
    public GroupMembership reviewRequest(
            Integer groupId,
            Integer membershipId,
            MembershipDecision decision,
            String message) {

        User user = currentUserResolver.currentUser();

        if (user.getRole() != Role.COACH) {
            throw new ForbiddenException(
                    "Solo los coaches pueden revisar solicitudes"
            );
        }

        if (decision == null) {
            throw new BadRequestException("La decisión es obligatoria");
        }

        StudyGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException(
                        "El grupo no existe"
                ));

        ensureResponsibleCoach(group, user);

        GroupMembership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new NotFoundException(
                        "La membresía no existe"
                ));

        if (!membership.getGroup().getId().equals(groupId)) {
            throw new BadRequestException(
                    "La membresía no pertenece a este grupo"
            );
        }

        return switch (decision) {
            case SOLICITAR_ACLARACION -> requestClarification(membership, message);
            case ACEPTAR -> accept(membership, group);
            case RECHAZAR -> reject(membership);
            case EXPULSAR -> expel(membership);
            case READMITIR -> readmit(membership, group);
        };
    }

    @Override
    @Transactional
    public GroupMembership respondClarification(
            Integer groupId,
            Integer membershipId,
            String response) {

        User user = currentUserResolver.currentUser();

        if (user.getRole() != Role.PRACTICANTE) {
            throw new ForbiddenException(
                    "Solo el postulante puede responder una aclaración"
            );
        }

        groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("El grupo no existe"));

        GroupMembership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new NotFoundException(
                        "La membresía no existe"
                ));

        if (!membership.getGroup().getId().equals(groupId)) {
            throw new BadRequestException(
                    "La membresía no pertenece a este grupo"
            );
        }

        if (!membership.getPractitioner().getUserId().equals(user.getId())) {
            throw new ForbiddenException(
                    "Solo el postulante puede responder una aclaración"
            );
        }

        if (membership.getStatus() != MembershipStatus.ACLARACION_SOLICITADA) {
            throw new ConflictException(
                    "La solicitud no está esperando una aclaración"
            );
        }

        String text = trimToNull(response);
        if (text == null) {
            throw new BadRequestException(
                    "La respuesta a la aclaración es obligatoria"
            );
        }

        membership.setClarificationResponse(text);
        membership.setClarificationRespondedAt(OffsetDateTime.now());
        membership.setStatus(MembershipStatus.PENDIENTE);

        return membershipRepository.save(membership);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupMemberData> findMembersByTeamId(Integer teamId) {
        return membershipRepository.findMembersByTeamId(teamId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<GroupMembership> findForUpdate(Integer teamId, Integer userId) {
        return membershipRepository.findForUpdate(teamId, userId);
    }

    private GroupMembership accept(GroupMembership membership, StudyGroup group) {
        requirePending(membership);

        if (group.getStatus() != GroupStatus.ACTIVO) {
            throw new ConflictException(
                    "No se pueden aceptar integrantes en un grupo que no está activo"
            );
        }

        ensureCapacity(group);
        membership.setStatus(MembershipStatus.ACTIVO);
        membership.setJoinedAt(OffsetDateTime.now());
        membership.setLeftAt(null);

        return membershipRepository.save(membership);
    }

    private GroupMembership reject(GroupMembership membership) {
        requirePending(membership);
        membership.setStatus(MembershipStatus.RECHAZADO);

        return membershipRepository.save(membership);
    }

    /** Expulsar solo aplica a un integrante ACTIVO; libera su cupo y bloquea el reingreso. */
    private GroupMembership expel(GroupMembership membership) {
        if (membership.getStatus() != MembershipStatus.ACTIVO) {
            throw new ConflictException(
                    "Solo se puede expulsar a un integrante activo del grupo"
            );
        }

        membership.setStatus(MembershipStatus.EXPULSADO);
        membership.setLeftAt(OffsetDateTime.now());

        return membershipRepository.save(membership);
    }

    /** El coach vuelve a incorporar a alguien expulsado; exige cupo y grupo activo. */
    private GroupMembership readmit(GroupMembership membership, StudyGroup group) {
        if (membership.getStatus() != MembershipStatus.EXPULSADO) {
            throw new ConflictException(
                    "Solo se puede readmitir a un integrante expulsado"
            );
        }

        if (group.getStatus() != GroupStatus.ACTIVO) {
            throw new ConflictException(
                    "No se puede readmitir integrantes en un grupo que no está activo"
            );
        }

        ensureCapacity(group);

        membership.setStatus(MembershipStatus.ACTIVO);
        membership.setJoinedAt(OffsetDateTime.now());
        membership.setLeftAt(null);
        clearClarification(membership);

        return membershipRepository.save(membership);
    }

    private GroupMembership requestClarification(GroupMembership membership, String message) {
        if (membership.getStatus() == MembershipStatus.ACLARACION_SOLICITADA) {
            throw new ConflictException(
                    "Ya se solicitó una aclaración a este postulante"
            );
        }

        requirePending(membership);

        String text = trimToNull(message);
        if (text == null) {
            throw new BadRequestException(
                    "Debes indicar qué aclaración necesitas del postulante"
            );
        }

        membership.setStatus(MembershipStatus.ACLARACION_SOLICITADA);
        membership.setClarificationRequest(text);
        membership.setClarificationRequestedAt(OffsetDateTime.now());
        membership.setClarificationResponse(null);
        membership.setClarificationRespondedAt(null);

        return membershipRepository.save(membership);
    }

    /** Aceptar o rechazar exige una solicitud PENDIENTE; una aclaración sin responder no se puede resolver. */
    private void requirePending(GroupMembership membership) {
        if (membership.getStatus() == MembershipStatus.ACLARACION_SOLICITADA) {
            throw new ConflictException(
                    "El postulante aún no respondió la aclaración solicitada"
            );
        }

        if (membership.getStatus() != MembershipStatus.PENDIENTE) {
            throw new ConflictException(
                    "Esta solicitud ya fue respondida"
            );
        }
    }

    private void ensureResponsibleCoach(StudyGroup group, User user) {
        if (!group.getCoach().getUserId().equals(user.getId())) {
            throw new ForbiddenException(
                    "No eres el coach responsable de este grupo"
            );
        }
    }

    private void ensureCapacity(StudyGroup group) {
        long activeMembers = membershipRepository.countByGroupIdAndStatus(
                group.getId(), MembershipStatus.ACTIVO
        );

        if (activeMembers >= group.getMaxCapacity()) {
            throw new ConflictException(
                    "El grupo alcanzo su capacidad maxima"
            );
        }
    }

    private static void clearClarification(GroupMembership membership) {
        membership.setClarificationRequest(null);
        membership.setClarificationRequestedAt(null);
        membership.setClarificationResponse(null);
        membership.setClarificationRespondedAt(null);
    }

    /** El código se compara sin importar espacios ni mayúsculas; se guarda en mayúsculas. */
    private static String normalizeInvitationCode(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new BadRequestException("El código de invitación es obligatorio");
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}


package com.kodika.kodikalab.teams.groupmembership;

import com.kodika.kodikalab.common.exception.BadRequestException;
import com.kodika.kodikalab.common.exception.ConflictException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.profiles.practitioner.PractitionerProfile;
import com.kodika.kodikalab.profiles.practitioner.PractitionerProfileService;
import com.kodika.kodikalab.teams.studygroup.GroupStatus;
import com.kodika.kodikalab.teams.studygroup.GroupVisibility;
import com.kodika.kodikalab.teams.studygroup.StudyGroup;
import com.kodika.kodikalab.teams.studygroup.StudyGroupRepository;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupMembershipServiceImpl implements GroupMembershipService {

    private final GroupMembershipRepository membershipRepository;
    private final StudyGroupRepository groupRepository;
    private final PractitionerProfileService practitionerProfileService;
    private final CurrentUserResolver currentUserResolver;

    public GroupMembershipServiceImpl(
            GroupMembershipRepository membershipRepository,
            StudyGroupRepository groupRepository,
            PractitionerProfileService practitionerProfileService,
            CurrentUserResolver currentUserResolver) {
        this.membershipRepository = membershipRepository;
        this.groupRepository = groupRepository;
        this.practitionerProfileService = practitionerProfileService;
        this.currentUserResolver = currentUserResolver;
    }

    @Override
    @Transactional
    public GroupMembership requestJoin(Integer groupId, String invitationCode) {
        User user = currentUserResolver.currentUser();

        if (user.getRole() != Role.PRACTICANTE) {
            throw new ForbiddenException(
                    "Solo los practicantes pueden solicitar ingreso"
            );
        }

        PractitionerProfile practitioner = practitionerProfileService
                .requirePractitionerProfile(user.getId());

        StudyGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException(
                        "El grupo solicitado no existe"
                ));

        if (group.getStatus() != GroupStatus.ACTIVO
                || group.getVisibility() == GroupVisibility.ARCHIVADO) {
            throw new ConflictException(
                    "No se puede ingresar a un grupo archivado"
            );
        }

        boolean directJoin = group.getVisibility() == GroupVisibility.PUBLICO
                || (invitationCode != null
                && invitationCode.equals(group.getInvitationCode()));

        GroupMembership membership = membershipRepository
                .findByGroupIdAndPractitionerUserId(groupId, user.getId())
                .orElseGet(GroupMembership::new);

        if (membership.getStatus() == MembershipStatus.ACTIVO) {
            throw new ConflictException("Ya perteneces a este grupo");
        }

        if (membership.getStatus() == MembershipStatus.PENDIENTE) {
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
        membership.setStatus(
                directJoin ? MembershipStatus.ACTIVO : MembershipStatus.PENDIENTE
        );

        return membershipRepository.save(membership);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupMembership> getPendingRequests(Integer groupId) {
        User user = currentUserResolver.currentUser();

        if (user.getRole() != Role.COACH) {
            throw new ForbiddenException(
                    "Solo los coaches pueden consultar solicitudes"
            );
        }

        StudyGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException(
                        "El grupo no existe"
                ));

        ensureResponsibleCoach(group, user);

        return membershipRepository.findByGroupIdAndStatus(
                groupId, MembershipStatus.PENDIENTE
        );
    }

    @Override
    @Transactional
    public GroupMembership reviewRequest(
            Integer groupId,
            Integer membershipId,
            boolean accept) {

        User user = currentUserResolver.currentUser();

        if (user.getRole() != Role.COACH) {
            throw new ForbiddenException(
                    "Solo los coaches pueden revisar solicitudes"
            );
        }

        StudyGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException(
                        "El grupo no existe"
                ));

        ensureResponsibleCoach(group, user);

        GroupMembership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new NotFoundException(
                        "La membresia no existe"
                ));

        if (!membership.getGroup().getId().equals(groupId)) {
            throw new BadRequestException(
                    "La membresia no pertenece a este grupo"
            );
        }

        if (membership.getStatus() != MembershipStatus.PENDIENTE) {
            throw new ConflictException(
                    "Esta solicitud ya fue respondida"
            );
        }

        if (accept) {
            if (group.getStatus() != GroupStatus.ACTIVO) {
                throw new ConflictException(
                        "No se pueden aceptar integrantes en un grupo archivado"
                );
            }

            ensureCapacity(group);
            membership.setStatus(MembershipStatus.ACTIVO);
            membership.setJoinedAt(OffsetDateTime.now());
            membership.setLeftAt(null);
        } else {
            membership.setStatus(MembershipStatus.RECHAZADO);
        }

        return membershipRepository.save(membership);
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
}


package com.kodika.kodikalab.teams.studygroup;

import com.kodika.kodikalab.common.exception.BadRequestException;
import com.kodika.kodikalab.common.exception.ConflictException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.profiles.coach.CoachProfile;
import com.kodika.kodikalab.profiles.coach.CoachProfileService;
import com.kodika.kodikalab.teams.groupmembership.GroupMembershipRepository;
import com.kodika.kodikalab.teams.groupmembership.MembershipStatus;
import com.kodika.kodikalab.teams.groupmembership.dto.MembershipCount;
import com.kodika.kodikalab.teams.studygroup.dto.MyTeamResponse;
import com.kodika.kodikalab.teams.studygroup.dto.StudyGroupSummary;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserStatus;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudyGroupServiceImpl implements StudyGroupService {

    private final StudyGroupRepository studyGroupRepository;
    private final CoachProfileService coachProfileService;
    private final CurrentUserResolver currentUserResolver;
    private final GroupMembershipRepository membershipRepository;

    public StudyGroupServiceImpl(
            StudyGroupRepository studyGroupRepository,
            CoachProfileService coachProfileService,
            CurrentUserResolver currentUserResolver,
            GroupMembershipRepository membershipRepository) {
        this.studyGroupRepository = studyGroupRepository;
        this.coachProfileService = coachProfileService;
        this.currentUserResolver = currentUserResolver;
        this.membershipRepository = membershipRepository;
    }

    @Override
    @Transactional
    public StudyGroup createGroup(CreateStudyGroupRequest request) {

        User user = currentUserResolver.currentUser();

        if (user.getRole() != Role.COACH) {
            throw new ForbiddenException("Solo un coach puede crear grupos");
        }

        CoachProfile coach = coachProfileService.requireCoachProfile(user.getId());

        if (request.visibility() == GroupVisibility.ARCHIVADO) {
            throw new BadRequestException(
                    "No se puede crear un grupo con visibilidad ARCHIVADO"
            );
        }

        StudyGroup group = new StudyGroup();

        group.setCoach(coach);
        applyFields(group, request);
        group.setVisibility(
                request.visibility() != null
                        ? request.visibility()
                        : GroupVisibility.PUBLICO
        );

        group.setStatus(
                request.isDraft() ? GroupStatus.BORRADOR : GroupStatus.ACTIVO
        );
        group.setCreatedAt(OffsetDateTime.now());
        group.setInvitationCode(newInvitationCode());

        // El borrador solo necesita nombre; el grupo publicado debe estar completo desde el inicio.
        if (request.isDraft()) {
            requireName(group);
        } else {
            validateReadyToPublish(group);
        }

        return studyGroupRepository.save(group);
    }

    @Override
    @Transactional
    public StudyGroup updateDraft(Integer groupId, UpdateStudyGroupDraftRequest request) {

        StudyGroup group = requireOwnedGroup(groupId);

        if (group.getStatus() != GroupStatus.BORRADOR) {
            throw new ConflictException(
                    "Solo se puede editar un grupo en borrador"
            );
        }

        if (request.visibility() == GroupVisibility.ARCHIVADO) {
            throw new BadRequestException(
                    "No se puede guardar un borrador con visibilidad ARCHIVADO"
            );
        }

        applyFields(group, request);
        requireName(group);

        return studyGroupRepository.save(group);
    }

    @Override
    @Transactional
    public StudyGroup publishGroup(Integer groupId) {

        StudyGroup group = requireOwnedGroup(groupId);

        if (group.getStatus() == GroupStatus.ACTIVO) {
            throw new ConflictException("El grupo ya está publicado");
        }

        if (group.getStatus() == GroupStatus.ARCHIVADO) {
            throw new ConflictException("No se puede publicar un grupo archivado");
        }

        validateReadyToPublish(group);

        if (group.getVisibility() == null || group.getVisibility() == GroupVisibility.ARCHIVADO) {
            group.setVisibility(GroupVisibility.PUBLICO);
        }

        group.setStatus(GroupStatus.ACTIVO);

        return studyGroupRepository.save(group);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudyGroup> getAvailableGroups() {
        return studyGroupRepository.findByStatusOrderByIdAsc(GroupStatus.ACTIVO);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MyTeamResponse> findMyTeams() {
        User user = currentUserResolver.currentUser();
        if (user.getStatus() != UserStatus.ACTIVO) {
            throw new ForbiddenException("La cuenta no está activa");
        }
        return user.getRole() == Role.COACH ? coachTeams(user) : practitionerTeams(user);
    }

    private List<MyTeamResponse> coachTeams(User coach) {
        List<StudyGroup> groups = studyGroupRepository.findByCoachUserId(coach.getId());
        if (groups.isEmpty()) {
            return List.of();
        }
        Map<Integer, Long> active = new HashMap<>();
        Map<Integer, Long> pending = new HashMap<>();
        List<Integer> ids = groups.stream().map(StudyGroup::getId).toList();
        for (MembershipCount count : membershipRepository.countActiveAndPendingByGroupIds(ids)) {
            (count.status() == MembershipStatus.ACTIVO ? active : pending).put(count.groupId(), count.total());
        }
        return groups.stream()
                .map(group -> MyTeamResponse.ofCoach(group, active.getOrDefault(group.getId(), 0L),
                        pending.getOrDefault(group.getId(), 0L)))
                .toList();
    }

    private List<MyTeamResponse> practitionerTeams(User practitioner) {
        return membershipRepository.findByPractitionerUserIdWithGroup(practitioner.getId()).stream()
                .map(MyTeamResponse::ofMember)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public void requireAccessibleTeam(Integer teamId) {
        User user = currentUserResolver.currentUser();
        if (user.getStatus() != UserStatus.ACTIVO || user.getId() == null
                || (user.getRole() != Role.COACH && user.getRole() != Role.PRACTICANTE)) {
            throw new ForbiddenException("La cuenta no tiene acceso a los equipos");
        }
        if (teamId == null || teamId <= 0) {
            throw new BadRequestException("El identificador del equipo debe ser un entero positivo");
        }
        StudyGroupSummary group = findSummaryById(teamId)
                .orElseThrow(() -> new NotFoundException("El equipo no existe"));
        boolean allowed = user.getRole() == Role.COACH
                ? user.getId().equals(group.coachUserId())
                : membershipRepository.existsByGroupIdAndPractitionerUserIdAndStatus(
                        teamId, user.getId(), MembershipStatus.ACTIVO);
        if (!allowed) {
            throw new ForbiddenException("No tiene autorización para consultar este equipo");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StudyGroupSummary> findSummaryById(Integer teamId) {
        return studyGroupRepository.findSummaryById(teamId);
    }

    /** Copia al grupo únicamente los campos enviados; {@code null} significa "no modificar". */
    private void applyFields(StudyGroup group, UpdateStudyGroupDraftRequest request) {
        if (request.name() != null) {
            group.setName(trimToNull(request.name()));
        }
        if (request.description() != null) {
            group.setDescription(trimToNull(request.description()));
        }
        if (request.expectedLevel() != null) {
            group.setExpectedLevel(trimToNull(request.expectedLevel()));
        }
        if (request.maxCapacity() != null) {
            group.setMaxCapacity(request.maxCapacity());
        }
        if (request.sessionSchedule() != null) {
            group.setSessionSchedule(trimToNull(request.sessionSchedule()));
        }
        if (request.visibility() != null) {
            group.setVisibility(request.visibility());
        }
    }

    /** Copia al grupo únicamente los campos enviados; {@code null} significa "no modificar". */
    private void applyFields(StudyGroup group, CreateStudyGroupRequest request) {
        if (request.name() != null) {
            group.setName(trimToNull(request.name()));
        }
        if (request.description() != null) {
            group.setDescription(trimToNull(request.description()));
        }
        if (request.expectedLevel() != null) {
            group.setExpectedLevel(trimToNull(request.expectedLevel()));
        }
        if (request.maxCapacity() != null) {
            group.setMaxCapacity(request.maxCapacity());
        }
        if (request.sessionSchedule() != null) {
            group.setSessionSchedule(trimToNull(request.sessionSchedule()));
        }
        if (request.visibility() != null) {
            group.setVisibility(request.visibility());
        }
    }

    private void requireName(StudyGroup group) {
        if (group.getName() == null || group.getName().isBlank()) {
            throw new BadRequestException(
                    "El nombre del grupo es obligatorio, incluso para guardar un borrador"
            );
        }
    }

    private void validateReadyToPublish(StudyGroup group) {
        requireName(group);

        if (group.getExpectedLevel() == null || group.getExpectedLevel().isBlank()) {
            throw new BadRequestException(
                    "El nivel esperado es obligatorio para publicar el grupo"
            );
        }

        if (group.getMaxCapacity() == null
                || group.getMaxCapacity() < 1
                || group.getMaxCapacity() > 1000) {
            throw new BadRequestException(
                    "El cupo máximo debe estar entre 1 y 1000 para publicar el grupo"
            );
        }

        if (group.getVisibility() == GroupVisibility.ARCHIVADO) {
            throw new BadRequestException(
                    "No se puede publicar un grupo con visibilidad ARCHIVADO"
            );
        }
    }

    private StudyGroup requireOwnedGroup(Integer groupId) {
        User user = currentUserResolver.currentUser();

        if (user.getRole() != Role.COACH) {
            throw new ForbiddenException("Solo un coach puede administrar grupos");
        }

        StudyGroup group = studyGroupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("El grupo no existe"));

        if (!group.getCoach().getUserId().equals(user.getId())) {
            throw new ForbiddenException(
                    "No eres el coach responsable de este grupo"
            );
        }

        return group;
    }

    private static String trimToNull(String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String newInvitationCode() {
        return UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase();
    }
}

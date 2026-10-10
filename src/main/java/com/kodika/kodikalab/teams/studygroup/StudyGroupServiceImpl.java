
package com.kodika.kodikalab.teams.studygroup;

import com.kodika.kodikalab.common.exception.BadRequestException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
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
        group.setName(request.name().trim());
        group.setDescription(request.description());
        group.setExpectedLevel(request.expectedLevel().trim());
        group.setMaxCapacity(request.maxCapacity());
        group.setSessionSchedule(request.sessionSchedule());

        group.setVisibility(
                request.visibility() != null
                        ? request.visibility()
                        : GroupVisibility.PUBLICO
        );

        group.setStatus(GroupStatus.ACTIVO);
        group.setCreatedAt(OffsetDateTime.now());

        String invitationCode = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase();

        group.setInvitationCode(invitationCode);

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
    public Optional<StudyGroupSummary> findSummaryById(Integer teamId) {
        return studyGroupRepository.findSummaryById(teamId);
    }
}

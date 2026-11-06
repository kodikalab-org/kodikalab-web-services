package com.kodika.kodikalab.teams.studygroup;

import com.kodika.kodikalab.common.exception.BadRequestException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.teams.groupmembership.GroupMembershipRepository;
import com.kodika.kodikalab.teams.groupmembership.MembershipStatus;
import com.kodika.kodikalab.teams.studygroup.dto.MyTeamResponse;
import com.kodika.kodikalab.teams.studygroup.dto.StudyGroupSummary;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudyGroupServiceImpl implements StudyGroupService {
    private final StudyGroupRepository studyGroupRepository;
    private final GroupMembershipRepository memberships;
    private final CurrentUserResolver currentUser;

    public StudyGroupServiceImpl(StudyGroupRepository studyGroupRepository,
                                 GroupMembershipRepository memberships, CurrentUserResolver currentUser) {
        this.studyGroupRepository = studyGroupRepository;
        this.memberships = memberships;
        this.currentUser = currentUser;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StudyGroupSummary> findSummaryById(Integer teamId) {
        return studyGroupRepository.findSummaryById(teamId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MyTeamResponse> findMyTeams() {
        User user = activeUser();
        if (user.getRole() == Role.COACH) {
            return studyGroupRepository.findByCoachUserIdOrderByIdAsc(user.getId()).stream()
                    .map(group -> response(group, group.getInvitationCode(),
                            memberships.countByGroupIdAndStatus(group.getId(), MembershipStatus.ACTIVO), null))
                    .toList();
        }
        return memberships.findByPractitionerUserIdAndStatusOrderByGroupIdAsc(user.getId(), MembershipStatus.ACTIVO)
                .stream().map(member -> response(member.getGroup(), null, null,
                        new MyTeamResponse.Membership(member.getId(), member.getStatus(), member.getTeamRole(),
                                member.getJoinedAt(), member.getLeftAt()))).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public void requireAccessibleTeam(Integer teamId) {
        User user = activeUser();
        if (teamId == null || teamId <= 0) {
            throw new BadRequestException("El identificador del equipo debe ser un entero positivo");
        }
        StudyGroupSummary group = findSummaryById(teamId)
                .orElseThrow(() -> new NotFoundException("El equipo no existe"));
        boolean allowed = user.getRole() == Role.COACH
                ? user.getId().equals(group.coachUserId())
                : memberships.existsByGroupIdAndPractitionerUserIdAndStatus(teamId, user.getId(), MembershipStatus.ACTIVO);
        if (!allowed) {
            throw new ForbiddenException("No tiene autorización para consultar este equipo");
        }
    }

    private User activeUser() {
        User user = currentUser.currentUser();
        if (user.getStatus() != UserStatus.ACTIVO || user.getId() == null
                || (user.getRole() != Role.COACH && user.getRole() != Role.PRACTICANTE)) {
            throw new ForbiddenException("La cuenta no tiene acceso a los equipos");
        }
        return user;
    }

    private MyTeamResponse response(StudyGroup group, String invitationCode, Long activeMembers,
                                    MyTeamResponse.Membership membership) {
        return new MyTeamResponse(group.getId(), group.getName(), group.getDescription(), group.getExpectedLevel(),
                group.getMaxCapacity(), group.getSessionSchedule(), group.getStatus(), group.getVisibility(),
                invitationCode, activeMembers, membership);
    }
}

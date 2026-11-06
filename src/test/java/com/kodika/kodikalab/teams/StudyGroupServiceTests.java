package com.kodika.kodikalab.teams;

import com.kodika.kodikalab.common.exception.*;
import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.teams.groupmembership.*;
import com.kodika.kodikalab.teams.studygroup.*;
import com.kodika.kodikalab.teams.studygroup.dto.StudyGroupSummary;
import com.kodika.kodikalab.users.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StudyGroupServiceTests {
    StudyGroupRepository groups = mock(StudyGroupRepository.class);
    GroupMembershipRepository members = mock(GroupMembershipRepository.class);
    CurrentUserResolver current = mock(CurrentUserResolver.class);
    StudyGroupServiceImpl service = new StudyGroupServiceImpl(groups, members, current);
    User user = new User();

    @BeforeEach
    void setUp() {
        user.setId(10);
        user.setStatus(UserStatus.ACTIVO);
        user.setRole(Role.COACH);
        when(current.currentUser()).thenReturn(user);
        when(groups.findSummaryById(1)).thenReturn(Optional.of(new StudyGroupSummary(1, 10)));
    }

    @Test
    void listsOnlyOwnedCoachGroupsWithRealMemberCounts() {
        StudyGroup group = new StudyGroup();
        group.setId(1);
        group.setName("Alfa");
        group.setInvitationCode("CODE");
        when(groups.findByCoachUserIdOrderByIdAsc(10)).thenReturn(List.of(group));
        when(members.countByGroupIdAndStatus(1, MembershipStatus.ACTIVO)).thenReturn(2L);
        var result = service.findMyTeams();
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().activeMembers()).isEqualTo(2L);
        assertThat(result.getFirst().invitationCode()).isEqualTo("CODE");
        assertThat(result.getFirst().membership()).isNull();
        verify(groups).findByCoachUserIdOrderByIdAsc(10);
    }

    @Test
    void practitionerUsesOnlyActiveMembershipsAndDoesNotReceiveInvitationCode() {
        user.setRole(Role.PRACTICANTE);
        StudyGroup group = new StudyGroup();
        group.setId(1);
        group.setName("Alfa");
        GroupMembership member = new GroupMembership();
        member.setId(20);
        member.setGroup(group);
        member.setStatus(MembershipStatus.ACTIVO);
        member.setTeamRole(TeamRole.MIEMBRO);
        when(members.findByPractitionerUserIdAndStatusOrderByGroupIdAsc(10, MembershipStatus.ACTIVO))
                .thenReturn(List.of(member));
        var result = service.findMyTeams().getFirst();
        assertThat(result.membership().membershipId()).isEqualTo(20);
        assertThat(result.invitationCode()).isNull();
        assertThat(result.activeMembers()).isNull();
        verifyNoInteractions(groups);
    }

    @Test
    void returnsEmptyForAccountWithoutTeams() {
        when(groups.findByCoachUserIdOrderByIdAsc(10)).thenReturn(List.of());
        assertThat(service.findMyTeams()).isEmpty();
    }

    @Test
    void ownerCanAccessButOtherCoachCannot() {
        service.requireAccessibleTeam(1);
        user.setId(11);
        assertThatThrownBy(() -> service.requireAccessibleTeam(1)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(members);
    }

    @Test
    void practitionerNeedsActiveMembership() {
        user.setRole(Role.PRACTICANTE);
        when(members.existsByGroupIdAndPractitionerUserIdAndStatus(1, 10, MembershipStatus.ACTIVO)).thenReturn(true);
        service.requireAccessibleTeam(1);
        when(members.existsByGroupIdAndPractitionerUserIdAndStatus(1, 10, MembershipStatus.ACTIVO)).thenReturn(false);
        assertThatThrownBy(() -> service.requireAccessibleTeam(1)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void rejectsSuspendedAndAnonymousBeforeReadingGroups() {
        user.setStatus(UserStatus.SUSPENDIDO);
        assertThatThrownBy(service::findMyTeams).isInstanceOf(ForbiddenException.class);
        when(current.currentUser()).thenThrow(new UnauthorizedException("Sesion requerida"));
        assertThatThrownBy(() -> service.requireAccessibleTeam(1)).isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(groups, members);
    }

    @Test
    void rejectsInvalidAndMissingTeams() {
        assertThatThrownBy(() -> service.requireAccessibleTeam(0)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.requireAccessibleTeam(null)).isInstanceOf(BadRequestException.class);
        when(groups.findSummaryById(2)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.requireAccessibleTeam(2)).isInstanceOf(NotFoundException.class);
    }
}

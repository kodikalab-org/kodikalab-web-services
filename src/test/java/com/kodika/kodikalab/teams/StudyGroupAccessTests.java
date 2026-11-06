package com.kodika.kodikalab.teams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kodika.kodikalab.common.exception.BadRequestException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.common.exception.UnauthorizedException;
import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.profiles.coach.CoachProfileService;
import com.kodika.kodikalab.teams.groupmembership.GroupMembershipRepository;
import com.kodika.kodikalab.teams.groupmembership.MembershipStatus;
import com.kodika.kodikalab.teams.studygroup.StudyGroupRepository;
import com.kodika.kodikalab.teams.studygroup.StudyGroupServiceImpl;
import com.kodika.kodikalab.teams.studygroup.dto.StudyGroupSummary;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserStatus;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Acceso a un equipo para consultar sus competencias y problemas (US-11 a US-14). */
class StudyGroupAccessTests {
    StudyGroupRepository groups = mock(StudyGroupRepository.class);
    GroupMembershipRepository members = mock(GroupMembershipRepository.class);
    CurrentUserResolver current = mock(CurrentUserResolver.class);
    StudyGroupServiceImpl service = new StudyGroupServiceImpl(groups, mock(CoachProfileService.class), current, members);
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
        assertThatThrownBy(() -> service.requireAccessibleTeam(1)).isInstanceOf(ForbiddenException.class);
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
        assertThat(user.getId()).isEqualTo(10);
    }
}

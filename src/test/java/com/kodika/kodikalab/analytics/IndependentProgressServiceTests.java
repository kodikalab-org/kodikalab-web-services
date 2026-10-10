package com.kodika.kodikalab.analytics;

import com.kodika.kodikalab.analytics.dto.TeamRankingResponse;
import com.kodika.kodikalab.analytics.dto.TeamRankingResponse.MemberStanding;
import com.kodika.kodikalab.analytics.dto.TeamRankingResponse.Status;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.UnauthorizedException;
import com.kodika.kodikalab.competitions.problemresolution.ProblemResolutionService;
import com.kodika.kodikalab.competitions.problemresolution.Verdict;
import com.kodika.kodikalab.competitions.problemresolution.dto.ManualResolutionRequest;
import com.kodika.kodikalab.competitions.problemresolution.dto.TeamResolutionData;
import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.teams.groupmembership.GroupMembershipService;
import com.kodika.kodikalab.teams.groupmembership.MembershipStatus;
import com.kodika.kodikalab.teams.groupmembership.dto.GroupMemberData;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IndependentProgressServiceTests {
    ProblemResolutionService resolutions;
    TeamRankingService rankings;
    GroupMembershipService memberships;
    CurrentUserResolver resolver;
    IndependentProgressService service;
    User user;
    ManualResolutionRequest request = new ManualResolutionRequest("Java 21", null);

    @BeforeEach
    void setUp() {
        resolutions = mock(ProblemResolutionService.class);
        rankings = mock(TeamRankingService.class);
        memberships = mock(GroupMembershipService.class);
        resolver = mock(CurrentUserResolver.class);
        service = new IndependentProgressService(resolutions, rankings, memberships, resolver);
        user = new User();
        user.setId(10);
        user.setRole(Role.PRACTICANTE);
        user.setStatus(UserStatus.ACTIVO);
        when(resolver.currentUser()).thenReturn(user);
        when(rankings.getRanking(1)).thenReturn(ranking(1, 20, 3));
        when(resolutions.registerManualAccepted(1, 50, request))
                .thenReturn(new TeamResolutionData(60, 20, 1, 50, 30, 1, 40, Verdict.ACCEPTED));
    }

    @Test
    void registrationCalculatesOnlySelectedTeamAndMarksManualOrigin() {
        var result = service.register(1, 50, request);
        assertThat(result.registrationMethod()).isEqualTo("MANUAL_PROVISIONAL");
        assertThat(result.resolution().membershipId()).isEqualTo(20);
        assertThat(result.progress().acceptedProblems()).isEqualTo(3);
        assertThat(result.progress().userId()).isEqualTo(10);
        var order = inOrder(resolutions, rankings);
        order.verify(resolutions).registerManualAccepted(1, 50, request);
        order.verify(rankings).getRanking(1);
        verify(rankings, never()).getRanking(2);
    }

    @Test
    void practitionerHasSeparateMembershipAndCountPerTeam() {
        when(rankings.getRanking(2)).thenReturn(ranking(2, 21, 1));
        var first = service.getProgress(1);
        var second = service.getProgress(2);
        assertThat(first.membershipId()).isEqualTo(20);
        assertThat(second.membershipId()).isEqualTo(21);
        assertThat(first.acceptedProblems()).isEqualTo(3);
        assertThat(second.acceptedProblems()).isEqualTo(1);
        verifyNoInteractions(resolutions);
    }

    @Test
    void teamWithoutActivityHasPersonalProgressZero() {
        when(rankings.getRanking(1)).thenReturn(new TeamRankingResponse(1, Status.NO_ACTIVITY, "criteria", "ties", List.of()));
        when(memberships.findMembersByTeamId(1)).thenReturn(List.of(
                new GroupMemberData(20, 1, 10, "Usuario Prueba", MembershipStatus.ACTIVO)));
        var progress = service.getProgress(1);
        assertThat(progress.membershipId()).isEqualTo(20);
        assertThat(progress.acceptedProblems()).isZero();
    }

    @Test
    void absentActiveMembershipCannotReturnAnotherMembersProgress() {
        when(rankings.getRanking(1)).thenReturn(new TeamRankingResponse(1, Status.CALCULATED, "criteria", "ties",
                List.of(new MemberStanding(22, 11, "Usuario Prueba", 9, 1))));
        assertThatThrownBy(() -> service.getProgress(1)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void invalidRegistrationDoesNotCalculateAnyTeam() {
        when(resolutions.registerManualAccepted(1, 50, request)).thenThrow(new ForbiddenException("Contexto inválido"));
        assertThatThrownBy(() -> service.register(1, 50, request)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(rankings);
    }

    @Test
    void invalidRankingPropagatesAndDoesNotReturnRegistrationSuccess() {
        when(rankings.getRanking(1)).thenThrow(new RankingDataException("Datos inconsistentes", Map.of("resolutions", "Equipo cruzado")));
        assertThatThrownBy(() -> service.register(1, 50, request)).isInstanceOf(RankingDataException.class);
    }

    @Test
    void mismatchedMembershipBetweenRegistrationAndProgressIsRejected() {
        when(rankings.getRanking(1)).thenReturn(ranking(1, 21, 3));
        assertThatThrownBy(() -> service.register(1, 50, request)).isInstanceOf(RankingDataException.class);
    }

    @Test
    void coachAndSuspendedUserCannotConsultOrRegisterPersonalProgress() {
        user.setRole(Role.COACH);
        assertForbidden();
        user.setRole(Role.PRACTICANTE);
        user.setStatus(UserStatus.SUSPENDIDO);
        assertForbidden();
        verifyNoInteractions(resolutions, rankings);
    }

    @Test
    void anonymousUserCannotConsultOrRegister() {
        when(resolver.currentUser()).thenThrow(new UnauthorizedException("Sesión requerida"));
        assertThatThrownBy(() -> service.getProgress(1)).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> service.register(1, 50, request)).isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(resolutions, rankings);
    }

    private void assertForbidden() {
        assertThatThrownBy(() -> service.getProgress(1)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.register(1, 50, request)).isInstanceOf(ForbiddenException.class);
    }

    private TeamRankingResponse ranking(int teamId, int membershipId, int accepted) {
        return new TeamRankingResponse(teamId, Status.CALCULATED, "criteria", "ties", List.of(
                new MemberStanding(22, 11, "Usuario Prueba", 9, 1),
                new MemberStanding(membershipId, 10, "Usuario Prueba", accepted, 2)));
    }
}

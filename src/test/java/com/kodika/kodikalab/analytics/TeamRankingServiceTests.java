package com.kodika.kodikalab.analytics;

import com.kodika.kodikalab.analytics.dto.TeamRankingResponse.Status;
import com.kodika.kodikalab.common.exception.BadRequestException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.common.exception.UnauthorizedException;
import com.kodika.kodikalab.competitions.problemresolution.ProblemResolutionService;
import com.kodika.kodikalab.competitions.problemresolution.Verdict;
import com.kodika.kodikalab.competitions.problemresolution.dto.TeamResolutionData;
import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.teams.groupmembership.GroupMembershipService;
import com.kodika.kodikalab.teams.groupmembership.MembershipStatus;
import com.kodika.kodikalab.teams.groupmembership.dto.GroupMemberData;
import com.kodika.kodikalab.teams.studygroup.StudyGroupService;
import com.kodika.kodikalab.teams.studygroup.dto.StudyGroupSummary;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserStatus;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TeamRankingServiceTests {
    CurrentUserResolver currentUser;
    StudyGroupService teams;
    GroupMembershipService memberships;
    ProblemResolutionService resolutions;
    TeamRankingService service;
    User requester;

    @BeforeEach
    void setUp() {
        currentUser = mock(CurrentUserResolver.class);
        teams = mock(StudyGroupService.class);
        memberships = mock(GroupMembershipService.class);
        resolutions = mock(ProblemResolutionService.class);
        service = new TeamRankingService(currentUser, teams, memberships, resolutions, new RankingService());
        requester = new User();
        requester.setId(10);
        requester.setRole(Role.PRACTICANTE);
        requester.setStatus(UserStatus.ACTIVO);
        when(currentUser.currentUser()).thenReturn(requester);
        when(teams.findSummaryById(1)).thenReturn(Optional.of(new StudyGroupSummary(1, 20)));
        when(memberships.findMembersByTeamId(1)).thenReturn(List.of(member(1, 10, MembershipStatus.ACTIVO),
                member(2, 11, MembershipStatus.ACTIVO), member(3, 12, MembershipStatus.RETIRADO)));
        when(resolutions.findResolutionsByTeamId(1)).thenReturn(List.of());
    }

    @Test
    void activeMemberGetsRankingUsingRealCalculatorAndPublicModuleServices() {
        when(resolutions.findResolutionsByTeamId(1)).thenReturn(List.of(
                resolution(1, 1, 10, 100, Verdict.ACCEPTED), resolution(2, 1, 11, 100, Verdict.ACCEPTED),
                resolution(3, 1, 12, 101, Verdict.ACCEPTED), resolution(4, 3, 13, 102, Verdict.ACCEPTED)));

        var response = service.getRanking(1);

        assertThat(response.status()).isEqualTo(Status.CALCULATED);
        assertThat(response.members()).hasSize(2);
        assertThat(response.members().getFirst().acceptedProblems()).isEqualTo(2);
        assertThat(response.members().get(1).acceptedProblems()).isZero();
        verify(teams).findSummaryById(1);
        verify(memberships).findMembersByTeamId(1);
        verify(resolutions).findResolutionsByTeamId(1);
    }

    @Test
    void responsibleCoachCanReadTeamWithoutActivity() {
        requester.setId(20);
        requester.setRole(Role.COACH);
        when(memberships.findMembersByTeamId(1)).thenReturn(List.of());

        assertThat(service.getRanking(1).status()).isEqualTo(Status.NO_ACTIVITY);
    }

    @Test
    void unrelatedCoachIsForbiddenBeforeReadingResolutions() {
        requester.setRole(Role.COACH);

        assertThatThrownBy(() -> service.getRanking(1)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(resolutions);
    }

    @Test
    void nonMemberIsForbiddenBeforeReadingResolutions() {
        requester.setId(99);

        assertThatThrownBy(() -> service.getRanking(1)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(resolutions);
    }

    @Test
    void inactiveMemberCannotReadRanking() {
        requester.setId(12);

        assertThatThrownBy(() -> service.getRanking(1)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(resolutions);
    }

    @Test
    void foreignMembershipDoesNotGrantPermission() {
        when(memberships.findMembersByTeamId(1)).thenReturn(List.of(
                new GroupMemberData(1, 2, 10, "Usuario Prueba", MembershipStatus.ACTIVO)));

        assertThatThrownBy(() -> service.getRanking(1)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(resolutions);
    }

    @Test
    void suspendedAccountIsForbiddenBeforeReadingTeam() {
        requester.setStatus(UserStatus.SUSPENDIDO);

        assertThatThrownBy(() -> service.getRanking(1)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(teams, memberships, resolutions);
    }

    @Test
    void anonymousRequestIsRejectedBeforeReadingData() {
        when(currentUser.currentUser()).thenThrow(new UnauthorizedException("Debe iniciar sesión"));

        assertThatThrownBy(() -> service.getRanking(1)).isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(teams, memberships, resolutions);
    }

    @Test
    void missingTeamIsNotFound() {
        when(teams.findSummaryById(1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRanking(1)).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(memberships, resolutions);
    }

    @Test
    void invalidTeamIdIsBadRequest() {
        for (Integer id : Arrays.asList(null, 0, -1)) {
            assertThatThrownBy(() -> service.getRanking(id)).isInstanceOf(BadRequestException.class);
        }
        verifyNoInteractions(teams, memberships, resolutions);
    }

    @Test
    void missingCoachRelationshipRejectsRanking() {
        when(teams.findSummaryById(1)).thenReturn(Optional.of(new StudyGroupSummary(1, null)));

        assertThatThrownBy(() -> service.getRanking(1)).isInstanceOfSatisfying(RankingDataException.class,
                exception -> assertThat(exception.getErrors()).containsKey("team.coachUserId"));
    }

    @ParameterizedTest
    @MethodSource("inconsistentResolutions")
    void inconsistentResolutionRejectsWholeRanking(TeamResolutionData resolution, String field) {
        when(resolutions.findResolutionsByTeamId(1)).thenReturn(List.of(
                resolution(1, 1, 10, 100, Verdict.ACCEPTED), resolution));

        assertThatThrownBy(() -> service.getRanking(1)).isInstanceOfSatisfying(RankingDataException.class,
                exception -> assertThat(exception.getErrors()).containsKey("resolutions[1]." + field));
    }

    static Stream<Arguments> inconsistentResolutions() {
        return Stream.of(
                Arguments.of(new TeamResolutionData(2, 1, 1, 10, 20, 2, 100, Verdict.ACCEPTED), "teamId"),
                Arguments.of(new TeamResolutionData(2, 1, 2, 10, 20, 1, 100, Verdict.ACCEPTED), "teamId"),
                Arguments.of(new TeamResolutionData(2, 1, null, 10, 20, 1, 100, Verdict.ACCEPTED), "teamId"),
                Arguments.of(new TeamResolutionData(2, 1, 1, null, 20, 1, 100, Verdict.ACCEPTED), "competitionProblemId"),
                Arguments.of(new TeamResolutionData(2, 1, 1, 10, null, 1, 100, Verdict.ACCEPTED), "competitionId"),
                Arguments.of(new TeamResolutionData(2, 99, 1, 10, 20, 1, 100, Verdict.ACCEPTED), "membershipId"),
                Arguments.of(new TeamResolutionData(2, 1, 1, 10, 20, 1, null, Verdict.ACCEPTED), "problemId"),
                Arguments.of(new TeamResolutionData(2, 1, 1, 10, 20, 1, 100, null), "verdict"));
    }

    @Test
    void unavailableDataDoesNotBecomeEmptyRanking() {
        when(resolutions.findResolutionsByTeamId(1)).thenReturn(null);
        assertThatThrownBy(() -> service.getRanking(1)).isInstanceOf(RankingDataException.class);
        when(memberships.findMembersByTeamId(1)).thenReturn(null);
        assertThatThrownBy(() -> service.getRanking(1)).isInstanceOf(RankingDataException.class);
    }

    @Test
    void persistenceFailureIsPropagatedInsteadOfReturningNoActivity() {
        when(resolutions.findResolutionsByTeamId(1)).thenThrow(new DataAccessResourceFailureException("Database down"));

        assertThatThrownBy(() -> service.getRanking(1)).isInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    void invalidMemberRejectsWholeRankingForAuthorizedCoach() {
        requester.setId(20);
        requester.setRole(Role.COACH);
        when(memberships.findMembersByTeamId(1)).thenReturn(Arrays.asList(member(1, 10, MembershipStatus.ACTIVO), null));

        assertThatThrownBy(() -> service.getRanking(1)).isInstanceOf(RankingDataException.class);
    }

    private GroupMemberData member(int membershipId, int userId, MembershipStatus status) {
        return new GroupMemberData(membershipId, 1, userId, "Usuario Prueba " + userId, status);
    }

    private TeamResolutionData resolution(int id, int membershipId, int competitionProblemId,
                                         int problemId, Verdict verdict) {
        return new TeamResolutionData(id, membershipId, 1, competitionProblemId, 20, 1, problemId, verdict);
    }
}

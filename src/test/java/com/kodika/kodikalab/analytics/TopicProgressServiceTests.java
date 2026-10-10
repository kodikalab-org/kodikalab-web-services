package com.kodika.kodikalab.analytics;

import com.kodika.kodikalab.common.exception.BadRequestException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.common.exception.UnauthorizedException;
import com.kodika.kodikalab.competitions.competition.CompetitionStatus;
import com.kodika.kodikalab.competitions.competitionproblem.CompetitionProblemService;
import com.kodika.kodikalab.competitions.competitionproblem.dto.TeamAssignedProblem;
import com.kodika.kodikalab.competitions.problemresolution.ProblemResolutionService;
import com.kodika.kodikalab.competitions.problemresolution.Verdict;
import com.kodika.kodikalab.competitions.problemresolution.dto.MemberAttempt;
import com.kodika.kodikalab.problems.problemtopic.ProblemTopicService;
import com.kodika.kodikalab.problems.problemtopic.dto.ProblemTopicData;
import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.teams.groupmembership.GroupMembershipService;
import com.kodika.kodikalab.teams.groupmembership.MembershipStatus;
import com.kodika.kodikalab.teams.groupmembership.dto.GroupMemberData;
import com.kodika.kodikalab.teams.studygroup.StudyGroupService;
import com.kodika.kodikalab.teams.studygroup.dto.StudyGroupSummary;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TopicProgressServiceTests {
    CurrentUserResolver currentUser = mock(CurrentUserResolver.class);
    StudyGroupService groups = mock(StudyGroupService.class);
    GroupMembershipService members = mock(GroupMembershipService.class);
    CompetitionProblemService assignments = mock(CompetitionProblemService.class);
    ProblemResolutionService resolutions = mock(ProblemResolutionService.class);
    ProblemTopicService topics = mock(ProblemTopicService.class);
    TopicProgressService service = new TopicProgressService(currentUser, groups, members, assignments, resolutions,
            topics, new TopicProgressCalculator());
    User practitioner;

    @BeforeEach
    void setUp() {
        practitioner = user(10, Role.PRACTICANTE, UserStatus.ACTIVO);
        when(currentUser.currentUser()).thenReturn(practitioner);
        when(groups.findSummaryById(1)).thenReturn(Optional.of(new StudyGroupSummary(1, 99)));
        when(members.findMembersByTeamId(1)).thenReturn(List.of(
                new GroupMemberData(6, 1, 11, "Otro integrante", MembershipStatus.ACTIVO),
                new GroupMemberData(7, 1, 10, "Ana Prueba", MembershipStatus.ACTIVO)));
        when(assignments.findAssignedProblemsByTeamId(1)).thenReturn(List.of(
                new TeamAssignedProblem(1, 50, 1, 100, CompetitionStatus.EN_CURSO),
                new TeamAssignedProblem(2, 50, 1, 101, CompetitionStatus.EN_CURSO)));
        when(topics.findTopicsByProblemIds(Set.of(100, 101))).thenReturn(List.of(
                new ProblemTopicData(100, 10, "Grafos"), new ProblemTopicData(101, 20, "Programación dinámica")));
        when(resolutions.findAttemptsByMembershipId(7)).thenReturn(List.of(
                new MemberAttempt(1, 1, Verdict.ACCEPTED, "Java 21", OffsetDateTime.parse("2026-10-10T10:00:00-05:00"),
                        null)));
    }

    @Test
    void calculatesTheOwnProgressFromTheMembershipOfTheRequesterOnly() {
        var result = service.getProgress(1);

        assertThat(result.membershipId()).isEqualTo(7);
        assertThat(result.userId()).isEqualTo(10);
        assertThat(result.assignedProblems()).isEqualTo(2);
        assertThat(result.solvedProblems()).isEqualTo(1);
        assertThat(result.topics()).extracting(t -> t.topicName()).containsExactly("Programación dinámica", "Grafos");
        verify(resolutions).findAttemptsByMembershipId(7);
        verify(resolutions, never()).findAttemptsByMembershipId(6);
        verify(topics).findTopicsByProblemIds(Set.of(100, 101));
    }

    @Test
    void aCoachCannotConsultThePractitionersProgress() {
        when(currentUser.currentUser()).thenReturn(user(20, Role.COACH, UserStatus.ACTIVO));

        assertThatThrownBy(() -> service.getProgress(1)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(groups, members, assignments, resolutions, topics);
    }

    @Test
    void aSuspendedPractitionerCannotConsultIt() {
        when(currentUser.currentUser()).thenReturn(user(10, Role.PRACTICANTE, UserStatus.SUSPENDIDO));

        assertThatThrownBy(() -> service.getProgress(1)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(groups, members, assignments, resolutions, topics);
    }

    @Test
    void anAnonymousRequestIsUnauthorized() {
        when(currentUser.currentUser()).thenThrow(new UnauthorizedException("Sin sesión"));

        assertThatThrownBy(() -> service.getProgress(1)).isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(groups, members, assignments, resolutions, topics);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void theTeamIdMustBePositive(int teamId) {
        assertThatThrownBy(() -> service.getProgress(teamId)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.getProgress(null)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(groups, members, assignments, resolutions, topics);
    }

    @Test
    void aMissingTeamIsNotFound() {
        when(groups.findSummaryById(2)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProgress(2)).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(members, assignments, resolutions, topics);
    }

    @ParameterizedTest
    @EnumSource(value = MembershipStatus.class, names = {"PENDIENTE", "RECHAZADO", "RETIRADO", "EXPULSADO"})
    void aMemberWhoIsNotActiveCannotConsultIt(MembershipStatus status) {
        when(members.findMembersByTeamId(1)).thenReturn(List.of(
                new GroupMemberData(7, 1, 10, "Ana Prueba", status)));

        assertThatThrownBy(() -> service.getProgress(1)).isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("membresía activa");
        verifyNoInteractions(assignments, resolutions, topics);
    }

    @Test
    void aPractitionerFromAnotherTeamCannotConsultIt() {
        when(members.findMembersByTeamId(1)).thenReturn(List.of(
                new GroupMemberData(6, 1, 11, "Otro integrante", MembershipStatus.ACTIVO)));

        assertThatThrownBy(() -> service.getProgress(1)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(assignments, resolutions, topics);
    }

    @Test
    void aTeamWithoutAssignmentsGetsAnEmptyProgress() {
        when(assignments.findAssignedProblemsByTeamId(1)).thenReturn(List.of());
        when(topics.findTopicsByProblemIds(Set.of())).thenReturn(List.of());
        when(resolutions.findAttemptsByMembershipId(7)).thenReturn(List.of());

        var result = service.getProgress(1);

        assertThat(result.assignedProblems()).isZero();
        assertThat(result.topics()).isEmpty();
    }

    @Test
    void missingAssignmentsAreADataProblemAndNothingIsCalculated() {
        when(assignments.findAssignedProblemsByTeamId(1)).thenReturn(null);

        assertThatThrownBy(() -> service.getProgress(1)).isInstanceOfSatisfying(TopicProgressDataException.class,
                exception -> assertThat(exception.getErrors()).containsKey("assignments"));
        verify(topics, never()).findTopicsByProblemIds(any());
    }

    @Test
    void inconsistentDataIsReportedInsteadOfShowingWrongIndicators() {
        when(resolutions.findAttemptsByMembershipId(7)).thenReturn(List.of(
                new MemberAttempt(1, 99, Verdict.ACCEPTED, "Java 21", null, null)));

        assertThatThrownBy(() -> service.getProgress(1)).isInstanceOfSatisfying(TopicProgressDataException.class,
                exception -> assertThat(exception.getErrors()).containsKey("attempts[0].competitionProblemId"));
    }

    @Test
    void anUnavailableDatabaseIsNotTurnedIntoAnEmptyProgress() {
        when(resolutions.findAttemptsByMembershipId(7)).thenThrow(new DataAccessResourceFailureException("SQL privado"));

        assertThatThrownBy(() -> service.getProgress(1)).isInstanceOf(DataAccessResourceFailureException.class);
    }

    private static User user(int id, Role role, UserStatus status) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setStatus(status);
        return user;
    }
}

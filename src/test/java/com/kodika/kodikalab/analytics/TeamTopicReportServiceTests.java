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
import com.kodika.kodikalab.competitions.problemresolution.dto.TeamResolutionData;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TeamTopicReportServiceTests {
    CurrentUserResolver currentUser = mock(CurrentUserResolver.class);
    StudyGroupService groups = mock(StudyGroupService.class);
    GroupMembershipService members = mock(GroupMembershipService.class);
    CompetitionProblemService assignments = mock(CompetitionProblemService.class);
    ProblemResolutionService resolutions = mock(ProblemResolutionService.class);
    ProblemTopicService topics = mock(ProblemTopicService.class);
    TeamTopicReportService service = new TeamTopicReportService(currentUser, groups, members, assignments,
            resolutions, topics, new TopicCoverageCalculator());
    User coach;

    @BeforeEach
    void setUp() {
        coach = new User();
        coach.setId(20);
        coach.setRole(Role.COACH);
        coach.setStatus(UserStatus.ACTIVO);
        when(currentUser.currentUser()).thenReturn(coach);
        when(groups.findSummaryById(1)).thenReturn(Optional.of(new StudyGroupSummary(1, 20)));
        when(members.findMembersByTeamId(1)).thenReturn(List.of(
                new GroupMemberData(1, 1, 10, "Usuario Prueba", MembershipStatus.ACTIVO)));
        when(assignments.findAssignedProblemsByTeamId(1)).thenReturn(List.of(
                new TeamAssignedProblem(1, 2, 1, 100, CompetitionStatus.FINALIZADA),
                new TeamAssignedProblem(2, 3, 1, 101, CompetitionStatus.PROGRAMADA)));
        when(topics.findTopicsByProblemIds(Set.of(100))).thenReturn(List.of(new ProblemTopicData(100, 10, "Grafos")));
        when(resolutions.findResolutionsByTeamId(1)).thenReturn(List.of(
                new TeamResolutionData(1, 1, 1, 1, 2, 1, 100, Verdict.ACCEPTED)));
    }

    @Test
    void authorizedCoachGetsReportFromPublicServicesWithOnlyCompletedProblemTopics() {
        var result = service.getReport(1);
        assertThat(result.teamId()).isEqualTo(1);
        assertThat(result.topics().getFirst().coveragePercentage()).isEqualByComparingTo("100");
        verify(topics).findTopicsByProblemIds(Set.of(100));
    }

    @Test
    void otherCoachIsRejectedBeforePerformanceDataIsRead() {
        coach.setId(21);
        assertThatThrownBy(() -> service.getReport(1)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(members, assignments, resolutions, topics);
    }

    @Test
    void practitionerCannotAccessReportEvenWhenMember() {
        coach.setRole(Role.PRACTICANTE);
        assertThatThrownBy(() -> service.getReport(1)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(groups, members, assignments, resolutions, topics);
    }

    @Test
    void suspendedCoachCannotAccessReport() {
        coach.setStatus(UserStatus.SUSPENDIDO);
        assertThatThrownBy(() -> service.getReport(1)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(groups, members, assignments, resolutions, topics);
    }

    @Test
    void anonymousRequestCannotReadTeam() {
        when(currentUser.currentUser()).thenThrow(new UnauthorizedException("Perfil"));
        assertThatThrownBy(() -> service.getReport(1)).isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(groups, members, assignments, resolutions, topics);
    }

    @Test
    void missingTeamReturnsNotFound() {
        when(groups.findSummaryById(1)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getReport(1)).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(members, assignments, resolutions, topics);
    }

    @Test
    void invalidTeamIdIsRejected() {
        assertThatThrownBy(() -> service.getReport(0)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.getReport(null)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(groups, members, assignments, resolutions, topics);
    }

    @Test
    void incompleteTeamInformationDoesNotAuthorizeCoach() {
        when(groups.findSummaryById(1)).thenReturn(Optional.of(new StudyGroupSummary(1, null)));
        assertThatThrownBy(() -> service.getReport(1)).isInstanceOf(TopicReportDataException.class);
        verifyNoInteractions(members, assignments, resolutions, topics);
    }

    @Test
    void unavailableAssignmentDataDoesNotProduceEmptyReport() {
        when(assignments.findAssignedProblemsByTeamId(1)).thenReturn(null);
        assertThatThrownBy(() -> service.getReport(1)).isInstanceOf(TopicReportDataException.class);
        verifyNoInteractions(topics, resolutions);
    }

    @Test
    void persistenceFailureIsPropagatedAndNextRequestCanSucceed() {
        when(resolutions.findResolutionsByTeamId(1)).thenThrow(new DataAccessResourceFailureException("SQL interno"));
        assertThatThrownBy(() -> service.getReport(1)).isInstanceOf(DataAccessResourceFailureException.class);
        doReturn(List.of(new TeamResolutionData(1, 1, 1, 1, 2, 1, 100, Verdict.ACCEPTED)))
                .when(resolutions).findResolutionsByTeamId(1);
        assertThat(service.getReport(1).topics()).hasSize(1);
    }
}

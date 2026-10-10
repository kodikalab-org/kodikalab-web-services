package com.kodika.kodikalab.competitions.problemresolution;

import com.kodika.kodikalab.common.exception.BadRequestException;
import com.kodika.kodikalab.common.exception.ConflictException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.common.exception.UnauthorizedException;
import com.kodika.kodikalab.competitions.competition.Competition;
import com.kodika.kodikalab.competitions.competition.CompetitionStatus;
import com.kodika.kodikalab.competitions.competitionproblem.CompetitionProblem;
import com.kodika.kodikalab.competitions.competitionproblem.CompetitionProblemRepository;
import com.kodika.kodikalab.competitions.problemresolution.dto.ManualResolutionRequest;
import com.kodika.kodikalab.competitions.problemresolution.dto.TeamResolutionData;
import com.kodika.kodikalab.problems.problem.Problem;
import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.profiles.practitioner.PractitionerProfile;
import com.kodika.kodikalab.teams.groupmembership.GroupMembership;
import com.kodika.kodikalab.teams.groupmembership.GroupMembershipService;
import com.kodika.kodikalab.teams.groupmembership.MembershipStatus;
import com.kodika.kodikalab.teams.studygroup.StudyGroup;
import com.kodika.kodikalab.teams.studygroup.StudyGroupService;
import com.kodika.kodikalab.teams.studygroup.dto.StudyGroupSummary;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserStatus;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManualResolutionServiceTests {
    ProblemResolutionRepository repository;
    CompetitionProblemRepository assignments;
    GroupMembershipService memberships;
    StudyGroupService groups;
    CurrentUserResolver resolver;
    ProblemResolutionService service;
    User user;
    GroupMembership membership;
    CompetitionProblem assignment;
    ManualResolutionRequest request = new ManualResolutionRequest(" Java 21 ", " https://example.com/submission/1 ");

    @BeforeEach
    void setUp() {
        repository = mock(ProblemResolutionRepository.class);
        assignments = mock(CompetitionProblemRepository.class);
        memberships = mock(GroupMembershipService.class);
        groups = mock(StudyGroupService.class);
        resolver = mock(CurrentUserResolver.class);
        service = new ProblemResolutionServiceImpl(repository, assignments, memberships, groups, resolver);
        user = new User();
        user.setId(10);
        user.setRole(Role.PRACTICANTE);
        user.setStatus(UserStatus.ACTIVO);
        when(resolver.currentUser()).thenReturn(user);
        StudyGroup group = new StudyGroup();
        group.setId(1);
        PractitionerProfile practitioner = new PractitionerProfile();
        practitioner.setUserId(10);
        membership = new GroupMembership();
        membership.setId(20);
        membership.setGroup(group);
        membership.setPractitioner(practitioner);
        membership.setStatus(MembershipStatus.ACTIVO);
        Competition competition = new Competition();
        competition.setId(30);
        competition.setGroup(group);
        competition.setStatus(CompetitionStatus.FINALIZADA);
        Problem problem = new Problem();
        problem.setId(40);
        assignment = new CompetitionProblem();
        assignment.setId(50);
        assignment.setCompetition(competition);
        assignment.setProblem(problem);
        when(groups.findSummaryById(1)).thenReturn(Optional.of(new StudyGroupSummary(1, 99)));
        when(memberships.findForUpdate(1, 10)).thenReturn(Optional.of(membership));
        when(assignments.findById(50)).thenReturn(Optional.of(assignment));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
            ProblemResolution resolution = invocation.getArgument(0);
            resolution.setId(60);
            return resolution;
        });
    }

    @Test
    void recordsAuthenticatedMembershipWithExplicitTeamAndAssignment() {
        assertThat(service.registerManualAccepted(1, 50, request))
                .isEqualTo(new TeamResolutionData(60, 20, 1, 50, 30, 1, 40, Verdict.ACCEPTED));
        var captor = org.mockito.ArgumentCaptor.forClass(ProblemResolution.class);
        verify(repository).saveAndFlush(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.getMembership()).isSameAs(membership);
        assertThat(saved.getCompetitionProblem()).isSameAs(assignment);
        assertThat(saved.getLanguage()).isEqualTo("Java 21");
        assertThat(saved.getEvidenceUrl()).isEqualTo("https://example.com/submission/1");
        assertThat(saved.getVerdict()).isEqualTo(Verdict.ACCEPTED);
        assertThat(saved.getSubmittedAt()).isNotNull();
        assertThat(saved.getExecutionTimeMs()).isNull();
        assertThat(saved.getMemoryUsedKb()).isNull();
        var order = inOrder(memberships, repository);
        order.verify(memberships).findForUpdate(1, 10);
        order.verify(repository).existsByMembershipIdAndCompetitionProblemIdAndVerdict(20, 50, Verdict.ACCEPTED);
        order.verify(repository).saveAndFlush(any());
    }

    @Test
    void competitionThatHasNotStartedRejectsTheRegistrationAndWritesNothing() {
        assignment.getCompetition().setStatus(CompetitionStatus.PROGRAMADA);

        assertThatThrownBy(() -> service.registerManualAccepted(1, 50, request))
                .isInstanceOf(ConflictException.class).hasMessageContaining("aún no ha comenzado");
        verify(repository, never()).existsByMembershipIdAndCompetitionProblemIdAndVerdict(any(), any(), any());
        verify(repository, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @EnumSource(value = CompetitionStatus.class, names = {"EN_CURSO", "FINALIZADA"})
    void competitionThatStartedOrFinishedAcceptsTheRegistration(CompetitionStatus status) {
        assignment.getCompetition().setStatus(status);

        assertThat(service.registerManualAccepted(1, 50, request).verdict()).isEqualTo(Verdict.ACCEPTED);
        verify(repository).saveAndFlush(any());
    }

    @Test
    void invalidFieldsAreReportedBeforeTheCompetitionStateIsChecked() {
        assignment.getCompetition().setStatus(CompetitionStatus.PROGRAMADA);

        assertThatThrownBy(() -> service.registerManualAccepted(1, 50, new ManualResolutionRequest(null, null)))
                .isInstanceOf(ResolutionValidationException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void acceptedDuplicateDoesNotAlterPreviousAttempts() {
        when(repository.existsByMembershipIdAndCompetitionProblemIdAndVerdict(20, 50, Verdict.ACCEPTED)).thenReturn(true);
        assertThatThrownBy(() -> service.registerManualAccepted(1, 50, request)).isInstanceOf(ConflictException.class);
        verify(repository, never()).saveAndFlush(any());
        verify(repository, never()).delete(any());
    }

    @Test
    void duplicateCheckDoesNotTreatPendingOrRejectedAttemptsAsAccepted() {
        service.registerManualAccepted(1, 50, new ManualResolutionRequest("Python 3", null));
        verify(repository).existsByMembershipIdAndCompetitionProblemIdAndVerdict(20, 50, Verdict.ACCEPTED);
        verify(repository).saveAndFlush(any());
    }

    @Test
    void sameCatalogProblemInDifferentCompetitionCanBeRecorded() {
        CompetitionProblem second = new CompetitionProblem();
        second.setId(51);
        Competition secondCompetition = new Competition();
        secondCompetition.setId(31);
        secondCompetition.setGroup(assignment.getCompetition().getGroup());
        secondCompetition.setStatus(CompetitionStatus.FINALIZADA);
        second.setCompetition(secondCompetition);
        second.setProblem(assignment.getProblem());
        when(assignments.findById(51)).thenReturn(Optional.of(second));
        service.registerManualAccepted(1, 50, request);
        service.registerManualAccepted(1, 51, request);
        verify(repository).existsByMembershipIdAndCompetitionProblemIdAndVerdict(20, 51, Verdict.ACCEPTED);
        verify(repository, times(2)).saveAndFlush(any());
    }

    @Test
    void assignmentFromOtherTeamCannotBeUsedEvenWithOwnMembership() {
        StudyGroup other = new StudyGroup();
        other.setId(2);
        assignment.getCompetition().setGroup(other);
        assertThatThrownBy(() -> service.registerManualAccepted(1, 50, request))
                .isInstanceOfSatisfying(ResolutionValidationException.class,
                        exception -> assertThat(exception.getErrors()).containsKey("teamId"));
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"RETIRADO", "EXPULSADO"})
    void inactiveMembershipCannotRecord(String status) {
        membership.setStatus(MembershipStatus.valueOf(status));
        assertThatThrownBy(() -> service.registerManualAccepted(1, 50, request)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(repository, assignments);
    }

    @Test
    void missingMembershipRequiresValidContext() {
        when(memberships.findForUpdate(1, 10)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.registerManualAccepted(1, 50, request))
                .isInstanceOf(ForbiddenException.class).hasMessageContaining("contexto de equipo válido");
        verifyNoInteractions(repository, assignments);
    }

    @Test
    void mismatchedMembershipUserOrTeamCannotRecord() {
        membership.getPractitioner().setUserId(11);
        assertThatThrownBy(() -> service.registerManualAccepted(1, 50, request)).isInstanceOf(ForbiddenException.class);
        membership.getPractitioner().setUserId(10);
        StudyGroup other = new StudyGroup();
        other.setId(2);
        membership.setGroup(other);
        assertThatThrownBy(() -> service.registerManualAccepted(1, 50, request)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(repository, assignments);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, -1})
    void invalidTeamOrAssignmentIdsDoNotWrite(Integer id) {
        assertThatThrownBy(() -> service.registerManualAccepted(id, 50, request)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.registerManualAccepted(1, id, request)).isInstanceOf(ResolutionValidationException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void missingTeamOrAssignmentDoesNotWrite() {
        when(groups.findSummaryById(1)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.registerManualAccepted(1, 50, request)).isInstanceOf(NotFoundException.class);
        when(groups.findSummaryById(1)).thenReturn(Optional.of(new StudyGroupSummary(1, 99)));
        when(assignments.findById(50)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.registerManualAccepted(1, 50, request)).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void incompleteAssignmentIsRejected() {
        assignment.setProblem(null);
        assertThatThrownBy(() -> service.registerManualAccepted(1, 50, request)).isInstanceOf(ConflictException.class);
        verifyNoInteractions(repository);
    }

    static Stream<Arguments> invalidRequests() {
        return Stream.of(Arguments.of(null, "body"),
                Arguments.of(new ManualResolutionRequest(null, null), "language"),
                Arguments.of(new ManualResolutionRequest("  ", null), "language"),
                Arguments.of(new ManualResolutionRequest("a".repeat(31), null), "language"),
                Arguments.of(new ManualResolutionRequest("Java 21", "not a URL"), "evidenceUrl"),
                Arguments.of(new ManualResolutionRequest("Java 21", "https://example.com/" + "a".repeat(500)), "evidenceUrl"));
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void invalidFieldsAreIdentifiedBeforeSaving(ManualResolutionRequest input, String field) {
        assertThatThrownBy(() -> service.registerManualAccepted(1, 50, input))
                .isInstanceOfSatisfying(ResolutionValidationException.class,
                        exception -> assertThat(exception.getErrors()).containsKey(field));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void evidenceIsOptionalAndEmptyEvidenceIsStoredAsAbsent() {
        service.registerManualAccepted(1, 50, new ManualResolutionRequest("Java 21", " "));
        var captor = org.mockito.ArgumentCaptor.forClass(ProblemResolution.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getEvidenceUrl()).isNull();
    }

    @Test
    void coachAndSuspendedPractitionerCannotRecord() {
        user.setRole(Role.COACH);
        assertThatThrownBy(() -> service.registerManualAccepted(1, 50, request)).isInstanceOf(ForbiddenException.class);
        user.setRole(Role.PRACTICANTE);
        user.setStatus(UserStatus.SUSPENDIDO);
        assertThatThrownBy(() -> service.registerManualAccepted(1, 50, request)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(repository, memberships, assignments);
    }

    @Test
    void anonymousRequestCannotRecord() {
        when(resolver.currentUser()).thenThrow(new UnauthorizedException("Sesión requerida"));
        assertThatThrownBy(() -> service.registerManualAccepted(1, 50, request)).isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(repository, memberships, assignments);
    }

    @Test
    void unavailableAssignmentIsNotTreatedAsMissingOrSaved() {
        when(assignments.findById(50)).thenThrow(new DataAccessResourceFailureException("SQL privado"));
        assertThatThrownBy(() -> service.registerManualAccepted(1, 50, request)).isInstanceOf(DataAccessResourceFailureException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void existingTeamReadRemainsDelegatedToRepositoryWithoutNewAuthorization() {
        var rows = List.of(new TeamResolutionData(60, 20, 1, 50, 30, 1, 40, Verdict.ACCEPTED));
        when(repository.findResolutionsByTeamId(1)).thenReturn(rows);
        assertThat(service.findResolutionsByTeamId(1)).isSameAs(rows);
        verifyNoInteractions(resolver, memberships, groups, assignments);
    }
}

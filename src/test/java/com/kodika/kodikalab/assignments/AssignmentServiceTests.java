package com.kodika.kodikalab.assignments;

import com.kodika.kodikalab.assignments.dto.AssignProblemsRequest;
import com.kodika.kodikalab.assignments.dto.AssignProblemsRequest.Item;
import com.kodika.kodikalab.common.exception.ConflictException;
import com.kodika.kodikalab.common.exception.FieldConflictException;
import com.kodika.kodikalab.common.exception.FieldValidationException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.common.exception.UnauthorizedException;
import com.kodika.kodikalab.competitions.competition.CompetitionService;
import com.kodika.kodikalab.competitions.competition.CompetitionStatus;
import com.kodika.kodikalab.competitions.competition.dto.CompetitionSummary;
import com.kodika.kodikalab.competitions.competitionproblem.CompetitionProblemService;
import com.kodika.kodikalab.competitions.competitionproblem.dto.AssignmentData;
import com.kodika.kodikalab.competitions.competitionproblem.dto.NewAssignment;
import com.kodika.kodikalab.problems.problem.ProblemService;
import com.kodika.kodikalab.problems.problem.SourcePlatform;
import com.kodika.kodikalab.problems.problem.dto.ProblemSummary;
import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.teams.studygroup.StudyGroupService;
import com.kodika.kodikalab.teams.studygroup.dto.StudyGroupSummary;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserStatus;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssignmentServiceTests {
    static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-20T14:00:00-05:00");

    CurrentUserResolver resolver;
    StudyGroupService groups;
    CompetitionService competitions;
    CompetitionProblemService competitionProblems;
    ProblemService problems;
    AssignmentService service;
    User coach;
    List<AssignmentData> existing;
    Set<Integer> missingFromCatalog;

    @BeforeEach
    void setUp() {
        resolver = mock(CurrentUserResolver.class);
        groups = mock(StudyGroupService.class);
        competitions = mock(CompetitionService.class);
        competitionProblems = mock(CompetitionProblemService.class);
        problems = mock(ProblemService.class);
        service = new AssignmentServiceImpl(resolver, groups, competitions, competitionProblems, problems);
        coach = user(10, Role.COACH, UserStatus.ACTIVO);
        existing = new ArrayList<>();
        missingFromCatalog = new HashSet<>();
        when(resolver.currentUser()).thenReturn(coach);
        when(competitions.findSummaryForUpdate(5))
                .thenReturn(Optional.of(new CompetitionSummary(5, 1, CompetitionStatus.PROGRAMADA)));
        when(groups.findSummaryById(1)).thenReturn(Optional.of(new StudyGroupSummary(1, 10)));
        when(problems.findSummariesByIds(any())).thenAnswer(invocation -> {
            Collection<Integer> ids = invocation.getArgument(0);
            return ids.stream().filter(id -> !missingFromCatalog.contains(id)).map(id -> summary(id)).toList();
        });
        when(competitionProblems.findByCompetitionId(5)).thenAnswer(invocation -> existing);
        when(competitionProblems.assign(eq(5), anyList())).thenAnswer(invocation -> {
            List<NewAssignment> requested = invocation.getArgument(1);
            List<AssignmentData> created = new ArrayList<>();
            for (int index = 0; index < requested.size(); index++) {
                NewAssignment item = requested.get(index);
                created.add(new AssignmentData(100 + index, 5, item.problemId(), item.letter(), item.score(),
                        item.balloonColor(), NOW));
            }
            return created;
        });
    }

    // ---- éxito

    @Test
    void assignsWithDefaultsAndConsecutiveLettersFromA() {
        var response = service.assign(request(5, item(11), item(12)));

        assertThat(response.competitionId()).isEqualTo(5);
        assertThat(response.teamId()).isEqualTo(1);
        assertThat(response.assigned()).extracting("problemId", "title", "letter", "score", "balloonColor")
                .containsExactly(org.assertj.core.groups.Tuple.tuple(11, "Problema 11", "A", 1, "#FF0000"),
                        org.assertj.core.groups.Tuple.tuple(12, "Problema 12", "B", 1, "#FF0000"));
        assertThat(response.assigned().get(0).competitionProblemId()).isEqualTo(100);
        assertThat(sent()).extracting(NewAssignment::letter).containsExactly("A", "B");
    }

    @Test
    void automaticLettersSkipLettersAlreadyUsedAndExplicitOnesOfTheSameRequest() {
        existing.add(assigned(1, 90, "A"));
        existing.add(assigned(2, 91, "C"));

        service.assign(request(5, item(11, " b ", null, null), item(12), item(13)));

        assertThat(sent()).extracting(NewAssignment::letter).containsExactly("B", "D", "E");
    }

    @Test
    void normalizesScoreAndColorAndKeepsTheRequestedValues() {
        service.assign(request(5, item(11, "x", 100, "#abc123")));

        NewAssignment sent = sent().get(0);
        assertThat(sent.letter()).isEqualTo("X");
        assertThat(sent.score()).isEqualTo(100);
        assertThat(sent.balloonColor()).isEqualTo("#ABC123");
    }

    @Test
    void acceptsTheMaximumNumberOfProblemsPerRequest() {
        List<Item> items = IntStream.rangeClosed(1, 50).mapToObj(id -> item(id)).toList();

        var response = service.assign(new AssignProblemsRequest(5, items));

        assertThat(response.assigned()).hasSize(50);
        assertThat(sent().get(49).letter()).isEqualTo("AX");
    }

    @Test
    void letterSequenceContinuesAfterZ() {
        assertThat(AssignmentServiceImpl.letterLabel(1)).isEqualTo("A");
        assertThat(AssignmentServiceImpl.letterLabel(26)).isEqualTo("Z");
        assertThat(AssignmentServiceImpl.letterLabel(27)).isEqualTo("AA");
        assertThat(AssignmentServiceImpl.letterLabel(52)).isEqualTo("AZ");
        assertThat(AssignmentServiceImpl.letterLabel(53)).isEqualTo("BA");
        assertThat(AssignmentServiceImpl.letterLabel(702)).isEqualTo("ZZ");
        assertThat(AssignmentServiceImpl.letterLabel(703)).isEqualTo("AAA");
        Set<String> taken = new HashSet<>(IntStream.rangeClosed(1, 26).mapToObj(AssignmentServiceImpl::letterLabel)
                .toList());
        assertThat(AssignmentServiceImpl.nextFreeLetter(taken)).isEqualTo("AA");
    }

    @Test
    void locksTheCompetitionSoConcurrentAssignmentsDoNotPickTheSameLetter() {
        service.assign(request(5, item(11)));

        verify(competitions).findSummaryForUpdate(5);
        verify(competitions, never()).findSummaryById(anyInt());
    }

    // ---- permisos

    @Test
    void absentSessionIsUnauthorized() {
        when(resolver.currentUser()).thenThrow(new UnauthorizedException("Sin sesión"));

        assertThatThrownBy(() -> service.assign(request(5, item(11)))).isInstanceOf(UnauthorizedException.class);
        verify(competitionProblems, never()).assign(anyInt(), anyList());
    }

    @ParameterizedTest
    @MethodSource("deniedUsers")
    void onlyAnActiveCoachCanAssign(User user) {
        when(resolver.currentUser()).thenReturn(user);

        assertThatThrownBy(() -> service.assign(request(5, item(11)))).isInstanceOf(ForbiddenException.class);
        verify(competitions, never()).findSummaryForUpdate(anyInt());
    }

    static Stream<User> deniedUsers() {
        return Stream.of(user(11, Role.PRACTICANTE, UserStatus.ACTIVO), user(10, Role.COACH, UserStatus.SUSPENDIDO));
    }

    @Test
    void aCoachWhoDoesNotManageTheTeamIsForbiddenAndNothingChanges() {
        when(groups.findSummaryById(1)).thenReturn(Optional.of(new StudyGroupSummary(1, 99)));

        assertThatThrownBy(() -> service.assign(request(5, item(11)))).isInstanceOf(ForbiddenException.class);
        verify(competitionProblems, never()).assign(anyInt(), anyList());
    }

    @Test
    void missingCompetitionIsNotFound() {
        when(competitions.findSummaryForUpdate(6)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assign(request(6, item(11)))).isInstanceOf(NotFoundException.class);
    }

    @Test
    void incompleteTeamInformationIsAConflict() {
        when(groups.findSummaryById(1)).thenReturn(Optional.of(new StudyGroupSummary(1, null)));
        assertThatThrownBy(() -> service.assign(request(5, item(11)))).isInstanceOf(ConflictException.class);

        when(competitions.findSummaryForUpdate(7)).thenReturn(Optional.of(
                new CompetitionSummary(7, null, CompetitionStatus.PROGRAMADA)));
        assertThatThrownBy(() -> service.assign(request(7, item(11)))).isInstanceOf(ConflictException.class);
    }

    @Test
    void aFinishedCompetitionDoesNotAcceptNewProblems() {
        when(competitions.findSummaryForUpdate(5))
                .thenReturn(Optional.of(new CompetitionSummary(5, 1, CompetitionStatus.FINALIZADA)));

        assertThatThrownBy(() -> service.assign(request(5, item(11)))).isInstanceOf(ConflictException.class)
                .hasMessageContaining("finalizó");
        verify(competitionProblems, never()).assign(anyInt(), anyList());
    }

    @Test
    void aCompetitionInProgressStillAcceptsProblems() {
        when(competitions.findSummaryForUpdate(5))
                .thenReturn(Optional.of(new CompetitionSummary(5, 1, CompetitionStatus.EN_CURSO)));

        assertThat(service.assign(request(5, item(11))).assigned()).hasSize(1);
    }

    // ---- validación

    @Test
    void missingBodyIsRejected() {
        assertThatThrownBy(() -> service.assign(null)).isInstanceOfSatisfying(FieldValidationException.class,
                exception -> assertThat(exception.getErrors()).containsKey("body"));
    }

    @ParameterizedTest
    @MethodSource("invalidCompetitionIds")
    void competitionIdIsRequiredAndPositive(Integer competitionId) {
        assertThatThrownBy(() -> service.assign(request(competitionId, item(11))))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        exception -> assertThat(exception.getErrors()).containsKey("competitionId"));
        verify(competitions, never()).findSummaryForUpdate(anyInt());
    }

    static Stream<Integer> invalidCompetitionIds() {
        return Stream.of(null, 0, -3);
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void invalidValuesAreRejectedWithThePathOfTheFieldToCorrect(AssignProblemsRequest request, String field) {
        assertThatThrownBy(() -> service.assign(request)).isInstanceOfSatisfying(FieldValidationException.class,
                exception -> assertThat(exception.getErrors()).containsKey(field));
        verify(competitionProblems, never()).assign(anyInt(), anyList());
    }

    static Stream<Arguments> invalidRequests() {
        return Stream.of(
                Arguments.of(new AssignProblemsRequest(5, null), "problems"),
                Arguments.of(new AssignProblemsRequest(5, List.of()), "problems"),
                Arguments.of(new AssignProblemsRequest(5, IntStream.rangeClosed(1, 51).mapToObj(id -> item(id)).toList()),
                        "problems"),
                Arguments.of(request(5, item(null)), "problems[0].problemId"),
                Arguments.of(request(5, item(0)), "problems[0].problemId"),
                Arguments.of(request(5, item(11), item(11)), "problems[1].problemId"),
                Arguments.of(request(5, item(11, "1A", null, null)), "problems[0].letter"),
                Arguments.of(request(5, item(11, "ABCDEF", null, null)), "problems[0].letter"),
                Arguments.of(request(5, item(11, "  ", null, null)), "problems[0].letter"),
                Arguments.of(request(5, item(11, "A", null, null), item(12, "a", null, null)), "problems[1].letter"),
                Arguments.of(request(5, item(11, null, 0, null)), "problems[0].score"),
                Arguments.of(request(5, item(11, null, -5, null)), "problems[0].score"),
                Arguments.of(request(5, item(11, null, null, "red")), "problems[0].balloonColor"),
                Arguments.of(request(5, item(11, null, null, "#12345")), "problems[0].balloonColor"),
                Arguments.of(request(5, item(11, null, null, "#GGGGGG")), "problems[0].balloonColor"));
    }

    @Test
    void reportsEveryInvalidItemFieldAtOnce() {
        var request = request(5, item(0, "9", 0, "x"), item(12, "A", null, null), item(13, "a", null, null));

        assertThatThrownBy(() -> service.assign(request)).isInstanceOfSatisfying(FieldValidationException.class,
                exception -> assertThat(exception.getErrors()).containsOnlyKeys("problems[0].problemId",
                        "problems[0].letter", "problems[0].score", "problems[0].balloonColor", "problems[2].letter"));
    }

    @Test
    void problemsOutsideTheCatalogAreRejectedAndNothingIsAssigned() {
        missingFromCatalog.add(12);

        assertThatThrownBy(() -> service.assign(request(5, item(11), item(12))))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        exception -> assertThat(exception.getErrors()).containsOnlyKeys("problems[1].problemId"));
        verify(competitionProblems, never()).assign(anyInt(), anyList());
    }

    // ---- conflictos

    @Test
    void aProblemAlreadyAssignedToTheCompetitionIsAConflictAndExistingAssignmentsAreKept() {
        existing.add(assigned(1, 11, "A"));

        assertThatThrownBy(() -> service.assign(request(5, item(11), item(12))))
                .isInstanceOfSatisfying(FieldConflictException.class,
                        exception -> assertThat(exception.getErrors()).containsOnlyKeys("problems[0].problemId"));
        verify(competitionProblems, never()).assign(anyInt(), anyList());
    }

    @Test
    void aLetterAlreadyInUseIsAConflict() {
        existing.add(assigned(1, 90, "B"));

        assertThatThrownBy(() -> service.assign(request(5, item(11), item(12, "b", null, null))))
                .isInstanceOfSatisfying(FieldConflictException.class,
                        exception -> assertThat(exception.getErrors()).containsOnlyKeys("problems[1].letter"));
        verify(competitionProblems, never()).assign(anyInt(), anyList());
    }

    @Test
    void reportsEveryConflictTogether() {
        existing.add(assigned(1, 11, "A"));

        assertThatThrownBy(() -> service.assign(request(5, item(11, "A", null, null), item(12))))
                .isInstanceOfSatisfying(FieldConflictException.class,
                        exception -> assertThat(exception.getErrors()).containsOnlyKeys("problems[0].problemId",
                                "problems[0].letter"));
        verify(competitionProblems, never()).assign(anyInt(), anyList());
    }

    // ---- utilidades

    private List<NewAssignment> sent() {
        ArgumentCaptor<List<NewAssignment>> captor = ArgumentCaptor.forClass(List.class);
        verify(competitionProblems).assign(eq(5), captor.capture());
        return captor.getValue();
    }

    private static ProblemSummary summary(int id) {
        return new ProblemSummary(id, "Problema " + id, "https://codeforces.com/p/" + id, SourcePlatform.CODEFORCES,
                "CF-" + id, "1000", 1000, 256);
    }

    private static AssignmentData assigned(int id, int problemId, String letter) {
        return new AssignmentData(id, 5, problemId, letter, 1, "#FF0000", NOW);
    }

    private static AssignProblemsRequest request(Integer competitionId, Item... items) {
        return new AssignProblemsRequest(competitionId, List.of(items));
    }

    private static Item item(Integer problemId) {
        return new Item(problemId, null, null, null);
    }

    private static Item item(Integer problemId, String letter, Integer score, String color) {
        return new Item(problemId, letter, score, color);
    }

    private static User user(int id, Role role, UserStatus status) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setStatus(status);
        return user;
    }
}

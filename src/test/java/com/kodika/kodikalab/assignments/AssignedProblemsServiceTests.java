package com.kodika.kodikalab.assignments;

import com.kodika.kodikalab.assignments.dto.AssignedProblemResponse;
import com.kodika.kodikalab.assignments.dto.AssignedProblemsQuery;
import com.kodika.kodikalab.common.exception.FieldValidationException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.common.exception.UnauthorizedException;
import com.kodika.kodikalab.competitions.competition.CompetitionAccessType;
import com.kodika.kodikalab.competitions.competition.CompetitionStatus;
import com.kodika.kodikalab.competitions.competition.PenaltyRule;
import com.kodika.kodikalab.competitions.competitionproblem.CompetitionProblemService;
import com.kodika.kodikalab.competitions.competitionproblem.dto.AssignmentView;
import com.kodika.kodikalab.competitions.problemresolution.ProblemResolutionService;
import com.kodika.kodikalab.competitions.problemresolution.Verdict;
import com.kodika.kodikalab.competitions.problemresolution.dto.MemberAttempt;
import com.kodika.kodikalab.problems.problem.ProblemService;
import com.kodika.kodikalab.problems.problem.SourcePlatform;
import com.kodika.kodikalab.problems.problem.dto.ProblemSummary;
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
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class AssignedProblemsServiceTests {
    static final OffsetDateTime DAY1 = OffsetDateTime.parse("2026-10-01T10:00:00-05:00");

    CurrentUserResolver resolver;
    StudyGroupService groups;
    GroupMembershipService memberships;
    CompetitionProblemService competitionProblems;
    ProblemService problems;
    ProblemTopicService topics;
    ProblemResolutionService resolutions;
    AssignedProblemsService service;
    User practitioner;
    AssignmentView cp1;
    AssignmentView cp2;
    AssignmentView cp3;

    @BeforeEach
    void setUp() {
        resolver = mock(CurrentUserResolver.class);
        groups = mock(StudyGroupService.class);
        memberships = mock(GroupMembershipService.class);
        competitionProblems = mock(CompetitionProblemService.class);
        problems = mock(ProblemService.class);
        topics = mock(ProblemTopicService.class);
        resolutions = mock(ProblemResolutionService.class);
        service = new AssignedProblemsServiceImpl(resolver, groups, memberships, competitionProblems, problems, topics,
                resolutions);

        practitioner = user(20, Role.PRACTICANTE, UserStatus.ACTIVO);
        when(resolver.currentUser()).thenReturn(practitioner);
        when(groups.findSummaryById(1)).thenReturn(Optional.of(new StudyGroupSummary(1, 10)));
        when(memberships.findMembersByTeamId(1)).thenReturn(List.of(
                new GroupMemberData(31, 1, 20, "Ana", MembershipStatus.ACTIVO),
                new GroupMemberData(32, 1, 21, "Beto", MembershipStatus.ACTIVO),
                new GroupMemberData(33, 1, 22, "Retirada", MembershipStatus.RETIRADO)));

        // Orden por defecto del repositorio: competencia más reciente primero y por letra.
        cp3 = view(3, 13, "A", 6, "Simulacro nuevo", CompetitionStatus.PROGRAMADA, DAY1.plusDays(9), DAY1.plusDays(7));
        cp1 = view(1, 11, "A", 5, "Simulacro base", CompetitionStatus.FINALIZADA, DAY1.plusDays(1), DAY1);
        cp2 = view(2, 12, "B", 5, "Simulacro base", CompetitionStatus.FINALIZADA, DAY1.plusDays(1), DAY1.plusDays(2));
        when(competitionProblems.findViewsByTeamId(1)).thenReturn(List.of(cp3, cp1, cp2));
        when(competitionProblems.findViewById(1)).thenReturn(Optional.of(cp1));
        when(competitionProblems.findViewById(3)).thenReturn(Optional.of(cp3));

        when(problems.findSummariesByIds(any())).thenAnswer(invocation -> {
            Collection<Integer> ids = invocation.getArgument(0);
            return Stream.of(summary(11, "Alfa", "800", "CF-A"), summary(12, "beta", "1400", "CF-B"),
                    summary(13, "Gamma", null, null)).filter(summary -> ids.contains(summary.id())).toList();
        });
        when(topics.findTopicsByProblemIds(any())).thenReturn(List.of(new ProblemTopicData(11, 1, "Grafos"),
                new ProblemTopicData(12, 2, "DP"), new ProblemTopicData(12, 1, "Grafos")));
        // Del más reciente al más antiguo, como los entrega el repositorio.
        when(resolutions.findAttemptsByMembershipId(31)).thenReturn(List.of(
                attempt(901, 1, Verdict.ACCEPTED, 5), attempt(902, 2, Verdict.PENDIENTE, 4),
                attempt(903, 1, Verdict.WRONG_ANSWER, 3), attempt(904, 2, Verdict.TLE, 2)));
    }

    // ---- listado del practicante

    @Test
    void practitionerSeesTheTeamAssignmentsWithPersonalStatusAndConditions() {
        var response = service.list(query(null, null, null, null, null, null, null, null));

        assertThat(response.teamId()).isEqualTo(1);
        assertThat(response.total()).isEqualTo(3);
        assertThat(ids(response.items())).containsExactly(3, 1, 2);

        AssignedProblemResponse solved = response.items().get(1);
        assertThat(solved.letter()).isEqualTo("A");
        assertThat(solved.status()).isEqualTo(AssignmentStatus.RESUELTO);
        assertThat(solved.attemptCount()).isEqualTo(2);
        assertThat(solved.lastAttempt().resolutionId()).isEqualTo(901);
        assertThat(solved.lastAttempt().verdict()).isEqualTo(Verdict.ACCEPTED);
        assertThat(solved.competition().name()).isEqualTo("Simulacro base");
        assertThat(solved.competition().status()).isEqualTo(CompetitionStatus.FINALIZADA);
        assertThat(solved.competition().durationMinutes()).isEqualTo(300);
        assertThat(solved.competition().penaltyRule()).isEqualTo(PenaltyRule.ICPC_20_MIN);
        assertThat(solved.competition().scoreboardFreezeMinutes()).isEqualTo(60);
        assertThat(solved.competition().accessType()).isEqualTo(CompetitionAccessType.PUBLICO_GRUPO);
        assertThat(solved.problem().title()).isEqualTo("Alfa");
        assertThat(solved.problem().timeLimitMs()).isEqualTo(1000);
        assertThat(solved.problem().memoryLimitMb()).isEqualTo(256);
        assertThat(solved.problem().topics()).containsExactly("Grafos");

        assertThat(response.items().get(2).status()).isEqualTo(AssignmentStatus.PENDIENTE);
        assertThat(response.items().get(2).problem().topics()).containsExactly("DP", "Grafos");
        AssignedProblemResponse untouched = response.items().get(0);
        assertThat(untouched.status()).isEqualTo(AssignmentStatus.SIN_INTENTOS);
        assertThat(untouched.attemptCount()).isZero();
        assertThat(untouched.lastAttempt()).isNull();
    }

    @Test
    void listingOnlyReadsAndNeverTouchesTheAssignmentsOrTheAttempts() {
        service.list(query(null, null, null, AssignmentStatus.RESUELTO, "alfa", "800", "title", "desc"));

        verify(resolutions).findAttemptsByMembershipId(31);
        verifyNoMoreInteractions(resolutions);
        verify(competitionProblems).findViewsByTeamId(1);
        verifyNoMoreInteractions(competitionProblems);
    }

    @Test
    void teamWithoutAssignmentsReturnsAnEmptyList() {
        when(competitionProblems.findViewsByTeamId(1)).thenReturn(List.of());

        var response = service.list(query(null, null, null, null, null, null, null, null));

        assertThat(response.total()).isZero();
        assertThat(response.items()).isEmpty();
        verify(problems, never()).findSummariesByIds(any());
    }

    // ---- estado derivado

    @Test
    void statusFollowsThePriorityResolvedOverPendingOverFailedOverNone() {
        assertThat(AssignedProblemsServiceImpl.statusOf(List.of())).isEqualTo(AssignmentStatus.SIN_INTENTOS);
        assertThat(AssignedProblemsServiceImpl.statusOf(List.of(attempt(1, 1, Verdict.WRONG_ANSWER, 1),
                attempt(2, 1, Verdict.TLE, 2)))).isEqualTo(AssignmentStatus.EN_PROGRESO);
        assertThat(AssignedProblemsServiceImpl.statusOf(List.of(attempt(1, 1, Verdict.ERROR, 1),
                attempt(2, 1, Verdict.PENDIENTE, 2)))).isEqualTo(AssignmentStatus.PENDIENTE);
        assertThat(AssignedProblemsServiceImpl.statusOf(List.of(attempt(1, 1, Verdict.PENDIENTE, 1),
                attempt(2, 1, Verdict.ACCEPTED, 2), attempt(3, 1, Verdict.MLE, 3)))).isEqualTo(AssignmentStatus.RESUELTO);
    }

    // ---- filtros

    @ParameterizedTest
    @MethodSource("filters")
    void filtersNarrowTheListWithoutChangingTheDefaultOrder(AssignedProblemsQuery query, List<Integer> expected) {
        assertThat(ids(service.list(query).items())).containsExactlyElementsOf(expected);
    }

    static Stream<Arguments> filters() {
        return Stream.of(
                Arguments.of(query(null, null, null, AssignmentStatus.RESUELTO, null, null, null, null), List.of(1)),
                Arguments.of(query(null, null, null, AssignmentStatus.PENDIENTE, null, null, null, null), List.of(2)),
                Arguments.of(query(null, null, null, AssignmentStatus.SIN_INTENTOS, null, null, null, null), List.of(3)),
                Arguments.of(query(null, null, null, AssignmentStatus.EN_PROGRESO, null, null, null, null), List.of()),
                Arguments.of(query(5, null, null, null, null, null, null, null), List.of(1, 2)),
                Arguments.of(query(null, CompetitionStatus.PROGRAMADA, null, null, null, null, null, null), List.of(3)),
                Arguments.of(query(null, null, null, null, "ALF", null, null, null), List.of(1)),
                Arguments.of(query(null, null, null, null, "cf-b", null, null, null), List.of(2)),
                Arguments.of(query(null, null, null, null, "zzz", null, null, null), List.of()),
                Arguments.of(query(null, null, null, null, "  ", null, null, null), List.of(3, 1, 2)),
                Arguments.of(query(null, null, null, null, null, "1400", null, null), List.of(2)),
                Arguments.of(query(null, null, null, null, null, "800", null, null), List.of(1)),
                Arguments.of(query(5, null, null, AssignmentStatus.PENDIENTE, null, null, null, null), List.of(2)),
                Arguments.of(query(5, null, null, AssignmentStatus.SIN_INTENTOS, null, null, null, null), List.of()));
    }

    // ---- orden

    @ParameterizedTest
    @MethodSource("sorts")
    void sortsByTheRequestedCriterionKeepingTiesInDefaultOrder(String sort, String order, List<Integer> expected) {
        assertThat(ids(service.list(query(null, null, null, null, null, null, sort, order)).items()))
                .containsExactlyElementsOf(expected);
    }

    static Stream<Arguments> sorts() {
        return Stream.of(
                Arguments.of("letter", null, List.of(3, 1, 2)),
                Arguments.of("letter", "asc", List.of(3, 1, 2)),
                Arguments.of("letter", "desc", List.of(2, 3, 1)),
                Arguments.of("title", "asc", List.of(1, 2, 3)),
                Arguments.of("title", "desc", List.of(3, 2, 1)),
                Arguments.of("difficulty", "asc", List.of(1, 2, 3)),
                Arguments.of("difficulty", "desc", List.of(2, 1, 3)),
                Arguments.of("assignedAt", "asc", List.of(1, 2, 3)),
                Arguments.of("assignedAt", "desc", List.of(3, 2, 1)),
                Arguments.of("status", "asc", List.of(3, 2, 1)),
                Arguments.of("status", "desc", List.of(1, 2, 3)),
                Arguments.of(null, "desc", List.of(3, 1, 2)));
    }

    @Test
    void letterAndDifficultyComparatorsAreTotalOrders() {
        assertThat(AssignedProblemsServiceImpl.compareLetters("Z", "AA")).isNegative();
        assertThat(AssignedProblemsServiceImpl.compareLetters("AB", "AA")).isPositive();
        assertThat(AssignedProblemsServiceImpl.compareLetters("B", "B")).isZero();
        assertThat(AssignedProblemsServiceImpl.compareDifficulty("800", "1400")).isNegative();
        assertThat(AssignedProblemsServiceImpl.compareDifficulty("1400", "800")).isPositive();
        assertThat(AssignedProblemsServiceImpl.compareDifficulty("1400", "*Medio")).isNegative();
        assertThat(AssignedProblemsServiceImpl.compareDifficulty("*Medio", "1400")).isPositive();
        assertThat(AssignedProblemsServiceImpl.compareDifficulty("*Alto", "*Medio")).isNegative();
    }

    // ---- coach

    @Test
    void theResponsibleCoachSeesTheAssignmentsWithoutPersonalProgress() {
        when(resolver.currentUser()).thenReturn(user(10, Role.COACH, UserStatus.ACTIVO));

        var response = service.list(query(null, null, null, null, null, null, null, null));

        assertThat(ids(response.items())).containsExactly(3, 1, 2);
        assertThat(response.items()).allSatisfy(item -> {
            assertThat(item.status()).isNull();
            assertThat(item.attemptCount()).isNull();
            assertThat(item.lastAttempt()).isNull();
        });
        verifyNoInteractions(memberships, resolutions);
    }

    @Test
    void personalStatusFilterIsNotAvailableToTheCoach() {
        when(resolver.currentUser()).thenReturn(user(10, Role.COACH, UserStatus.ACTIVO));

        assertThatThrownBy(() -> service.list(query(null, null, null, AssignmentStatus.RESUELTO, null, null, null, null)))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        exception -> assertThat(exception.getErrors()).containsKey("status"));
    }

    // ---- acceso

    @Test
    void absentSessionIsUnauthorized() {
        when(resolver.currentUser()).thenThrow(new UnauthorizedException("Sin sesión"));

        assertThatThrownBy(() -> service.list(query(null, null, null, null, null, null, null, null)))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> service.detail(1)).isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(competitionProblems);
    }

    @ParameterizedTest
    @MethodSource("deniedUsers")
    void usersWithoutAccessToTheTeamAreForbiddenAndNothingIsRead(User user) {
        when(resolver.currentUser()).thenReturn(user);

        assertThatThrownBy(() -> service.list(query(null, null, null, null, null, null, null, null)))
                .isInstanceOf(ForbiddenException.class);
        verify(competitionProblems, never()).findViewsByTeamId(anyInt());
        verifyNoInteractions(resolutions);
    }

    static Stream<User> deniedUsers() {
        return Stream.of(
                user(20, Role.PRACTICANTE, UserStatus.SUSPENDIDO),   // cuenta suspendida
                user(99, Role.PRACTICANTE, UserStatus.ACTIVO),       // no pertenece al equipo
                user(22, Role.PRACTICANTE, UserStatus.ACTIVO),       // integrante RETIRADO
                user(99, Role.COACH, UserStatus.ACTIVO));            // coach de otro equipo
    }

    @Test
    void missingTeamIsNotFound() {
        when(groups.findSummaryById(7)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.list(query(null, null, 7, null, null, null, null, null)))
                .isInstanceOf(NotFoundException.class);
    }

    // ---- validación

    @ParameterizedTest
    @MethodSource("invalidQueries")
    void invalidCriteriaAreRejectedBeforeReadingAnything(AssignedProblemsQuery query, String field) {
        assertThatThrownBy(() -> service.list(query)).isInstanceOfSatisfying(FieldValidationException.class,
                exception -> assertThat(exception.getErrors()).containsKey(field));
        verifyNoInteractions(competitionProblems);
    }

    static Stream<Arguments> invalidQueries() {
        return Stream.of(
                Arguments.of(new AssignedProblemsQuery(null, null, null, null, null, null, null, null), "teamId"),
                Arguments.of(new AssignedProblemsQuery(0, null, null, null, null, null, null, null), "teamId"),
                Arguments.of(new AssignedProblemsQuery(1, 0, null, null, null, null, null, null), "competitionId"),
                Arguments.of(new AssignedProblemsQuery(1, null, null, null, "q".repeat(101), null, null, null), "q"),
                Arguments.of(new AssignedProblemsQuery(1, null, null, null, null, "d".repeat(31), null, null),
                        "difficulty"),
                Arguments.of(new AssignedProblemsQuery(1, null, null, null, null, null, "random", null), "sort"),
                Arguments.of(new AssignedProblemsQuery(1, null, null, null, null, null, null, "up"), "order"));
    }

    // ---- detalle

    @Test
    void detailShowsTheAssignmentAndTheOwnAttemptsNewestFirst() {
        var detail = service.detail(1);

        assertThat(detail.assignment().competitionProblemId()).isEqualTo(1);
        assertThat(detail.assignment().status()).isEqualTo(AssignmentStatus.RESUELTO);
        assertThat(detail.attempts()).extracting("resolutionId").containsExactly(901, 903);
        assertThat(detail.attempts()).extracting("verdict").containsExactly(Verdict.ACCEPTED, Verdict.WRONG_ANSWER);
        assertThat(detail.attempts().get(0).language()).isEqualTo("Java");
    }

    @Test
    void detailOfAnUntouchedProblemHasNoAttempts() {
        var detail = service.detail(3);

        assertThat(detail.assignment().status()).isEqualTo(AssignmentStatus.SIN_INTENTOS);
        assertThat(detail.attempts()).isEmpty();
    }

    @Test
    void detailForTheCoachHasNoPersonalProgress() {
        when(resolver.currentUser()).thenReturn(user(10, Role.COACH, UserStatus.ACTIVO));

        var detail = service.detail(1);

        assertThat(detail.assignment().status()).isNull();
        assertThat(detail.attempts()).isEmpty();
        verifyNoInteractions(resolutions);
    }

    @Test
    void detailOfAnUnknownAssignmentIsNotFound() {
        when(competitionProblems.findViewById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(99)).isInstanceOf(NotFoundException.class);
    }

    @ParameterizedTest
    @MethodSource("invalidIds")
    void detailRequiresAPositiveId(Integer id) {
        assertThatThrownBy(() -> service.detail(id)).isInstanceOfSatisfying(FieldValidationException.class,
                exception -> assertThat(exception.getErrors()).containsKey("competitionProblemId"));
    }

    static Stream<Integer> invalidIds() {
        return Stream.of(null, 0, -4);
    }

    @Test
    void detailOfAnotherTeamsAssignmentIsForbidden() {
        AssignmentView foreign = view(50, 13, "A", 8, 2, "Ajena", CompetitionStatus.EN_CURSO, DAY1, DAY1);
        when(competitionProblems.findViewById(50)).thenReturn(Optional.of(foreign));
        when(groups.findSummaryById(2)).thenReturn(Optional.of(new StudyGroupSummary(2, 99)));
        when(memberships.findMembersByTeamId(2)).thenReturn(List.of(
                new GroupMemberData(40, 2, 77, "Otro", MembershipStatus.ACTIVO)));

        assertThatThrownBy(() -> service.detail(50)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(resolutions);
    }

    @Test
    void anAssignmentWhoseProblemIsMissingFromTheCatalogIsAnInconsistency() {
        doReturn(List.of()).when(problems).findSummariesByIds(any());

        assertThatThrownBy(() -> service.list(query(null, null, null, null, null, null, null, null)))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---- utilidades

    private static List<Integer> ids(List<AssignedProblemResponse> items) {
        return items.stream().map(AssignedProblemResponse::competitionProblemId).toList();
    }

    /** Consulta del equipo 1 (o del equipo indicado en {@code team}); los filtros {@code null} no se aplican. */
    private static AssignedProblemsQuery query(Integer competitionId, CompetitionStatus competitionStatus, Integer team,
                                               AssignmentStatus status, String text, String difficulty, String sort,
                                               String order) {
        return new AssignedProblemsQuery(team == null ? 1 : team, competitionId, competitionStatus, status, text,
                difficulty, sort, order);
    }

    private static ProblemSummary summary(int id, String title, String difficulty, String code) {
        return new ProblemSummary(id, title, "https://codeforces.com/p/" + id, SourcePlatform.CODEFORCES, code,
                difficulty, 1000, 256);
    }

    private static MemberAttempt attempt(int resolutionId, int competitionProblemId, Verdict verdict, int minute) {
        return new MemberAttempt(resolutionId, competitionProblemId, verdict, "Java", DAY1.plusMinutes(minute),
                "https://evidence/" + resolutionId);
    }

    private static AssignmentView view(int id, int problemId, String letter, int competitionId, String name,
                                       CompetitionStatus status, OffsetDateTime startsAt, OffsetDateTime assignedAt) {
        return view(id, problemId, letter, competitionId, 1, name, status, startsAt, assignedAt);
    }

    private static AssignmentView view(int id, int problemId, String letter, int competitionId, int teamId,
                                       String name, CompetitionStatus status, OffsetDateTime startsAt,
                                       OffsetDateTime assignedAt) {
        return new AssignmentView(id, problemId, letter, 100, "#FF0000", assignedAt, competitionId, teamId, name,
                status, startsAt, startsAt.plusHours(5), 300, PenaltyRule.ICPC_20_MIN, 60,
                CompetitionAccessType.PUBLICO_GRUPO);
    }

    private static User user(int id, Role role, UserStatus status) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setStatus(status);
        return user;
    }
}

package com.kodika.kodikalab.assignments;

import com.kodika.kodikalab.assignments.dto.CompetitionStandingsResponse;
import com.kodika.kodikalab.assignments.dto.CompetitionStandingsResponse.Cell;
import com.kodika.kodikalab.assignments.dto.CompetitionStandingsResponse.Row;
import com.kodika.kodikalab.common.exception.FieldValidationException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.competitions.competition.CompetitionService;
import com.kodika.kodikalab.competitions.competition.CompetitionStatus;
import com.kodika.kodikalab.competitions.competition.PenaltyRule;
import com.kodika.kodikalab.competitions.competition.dto.CompetitionDetail;
import com.kodika.kodikalab.competitions.competitionproblem.CompetitionProblemService;
import com.kodika.kodikalab.competitions.competitionproblem.dto.AssignmentData;
import com.kodika.kodikalab.competitions.problemresolution.ProblemResolutionService;
import com.kodika.kodikalab.competitions.problemresolution.Verdict;
import com.kodika.kodikalab.competitions.problemresolution.dto.CompetitionAttempt;
import com.kodika.kodikalab.problems.problem.ProblemService;
import com.kodika.kodikalab.problems.problem.SourcePlatform;
import com.kodika.kodikalab.problems.problem.dto.ProblemSummary;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CompetitionStandingsServiceTests {
    static final OffsetDateTime START = OffsetDateTime.parse("2026-10-20T14:00:00-05:00");
    static final OffsetDateTime END = START.plusMinutes(300);

    CurrentUserResolver resolver;
    CompetitionService competitions;
    StudyGroupService groups;
    GroupMembershipService memberships;
    CompetitionProblemService competitionProblems;
    ProblemService problems;
    ProblemResolutionService resolutions;
    CompetitionStandingsService service;

    @BeforeEach
    void setUp() {
        resolver = mock(CurrentUserResolver.class);
        competitions = mock(CompetitionService.class);
        groups = mock(StudyGroupService.class);
        memberships = mock(GroupMembershipService.class);
        competitionProblems = mock(CompetitionProblemService.class);
        problems = mock(ProblemService.class);
        resolutions = mock(ProblemResolutionService.class);
        service = new CompetitionStandingsServiceImpl(resolver, competitions, groups, memberships,
                competitionProblems, problems, resolutions);

        when(resolver.currentUser()).thenReturn(user(10, Role.COACH, UserStatus.ACTIVO));
        when(groups.findSummaryById(3)).thenReturn(Optional.of(new StudyGroupSummary(3, 10)));
        when(competitions.findDetailById(5)).thenReturn(Optional.of(detail(PenaltyRule.ICPC_20_MIN)));
        // El repositorio entrega las letras en orden alfabético de texto: "AA" antes de "B".
        when(competitionProblems.findByCompetitionId(5)).thenReturn(List.of(
                new AssignmentData(12, 5, 102, "B", 200, "#00FF00", START),
                new AssignmentData(13, 5, 103, "AA", 50, "#0000FF", START),
                new AssignmentData(11, 5, 101, "A", 100, "#FF0000", START)));
        when(problems.findSummariesByIds(any())).thenAnswer(invocation -> {
            Collection<Integer> ids = invocation.getArgument(0);
            return Stream.of(summary(101, "Watermelon"), summary(102, "Way Too Long Words"),
                    summary(103, "Team")).filter(summary -> ids.contains(summary.id())).toList();
        });
        when(memberships.findMembersByTeamId(3)).thenReturn(List.of(
                member(31, 7, "Ana Pérez", MembershipStatus.ACTIVO),
                member(32, 8, "Beto Ríos", MembershipStatus.ACTIVO),
                member(33, 9, "Carla Soto", MembershipStatus.ACTIVO),
                member(34, 6, "Retirado Uno", MembershipStatus.RETIRADO),
                member(35, 5, "Pendiente Dos", MembershipStatus.PENDIENTE)));
        when(resolutions.findAttemptsByCompetition(anyInt(), anyInt())).thenReturn(List.of());
    }

    // ---- contrato y problemas

    @Test
    void exposesTheCompetitionAndItsProblemsOrderedByLetter() {
        CompetitionStandingsResponse response = service.standings(5);

        assertThat(response.competitionId()).isEqualTo(5);
        assertThat(response.teamId()).isEqualTo(3);
        assertThat(response.eventName()).isEqualTo("Semanal 12");
        assertThat(response.status()).isEqualTo(CompetitionStatus.EN_CURSO);
        assertThat(response.penaltyRule()).isEqualTo(PenaltyRule.ICPC_20_MIN);
        assertThat(response.startsAt()).isEqualTo(START);
        assertThat(response.endsAt()).isEqualTo(END);
        assertThat(response.problems()).extracting("letter").containsExactly("A", "B", "AA");
        assertThat(response.problems()).extracting("competitionProblemId").containsExactly(11, 12, 13);
        assertThat(response.problems()).extracting("title")
                .containsExactly("Watermelon", "Way Too Long Words", "Team");
        assertThat(response.problems()).extracting("score").containsExactly(100, 200, 50);
    }

    @Test
    void listsEveryActiveMemberEvenWithoutAttemptsAndOnlyThem() {
        CompetitionStandingsResponse response = service.standings(5);

        assertThat(response.rows()).extracting(Row::userId).containsExactly(7, 8, 9);
        assertThat(response.rows()).extracting(Row::fullName).containsExactly("Ana Pérez", "Beto Ríos", "Carla Soto");
        assertThat(response.rows()).extracting(Row::position).containsExactly(1, 1, 1);
        Row row = response.rows().get(0);
        assertThat(row.solved()).isZero();
        assertThat(row.score()).isZero();
        assertThat(row.penaltyMinutes()).isZero();
        assertThat(row.cells()).extracting(Cell::competitionProblemId).containsExactly(11, 12, 13);
        assertThat(row.cells()).extracting(Cell::letter).containsExactly("A", "B", "AA");
        assertThat(row.cells()).extracting(Cell::result).containsOnly(AssignmentStatus.SIN_INTENTOS);
        assertThat(row.cells()).extracting(Cell::attempts).containsOnly(0);
        assertThat(row.cells()).extracting(Cell::minutes).containsOnlyNulls();
    }

    // ---- ICPC

    @Test
    void icpcRanksBySolvedThenPenaltyCountingWrongAttemptsBeforeTheAcceptance() {
        when(resolutions.findAttemptsByCompetition(5, 3)).thenReturn(List.of(
                // Ana: A tras un fallo (12 + 20), B a la primera (30); un intento posterior al aceptado no cuenta.
                attempt(1, 31, 11, Verdict.WRONG_ANSWER, 5), attempt(2, 31, 11, Verdict.ACCEPTED, 12),
                attempt(3, 31, 12, Verdict.ACCEPTED, 30), attempt(4, 31, 11, Verdict.WRONG_ANSWER, 40),
                // Beto: solo A, a los 10 minutos.
                attempt(5, 32, 11, Verdict.ACCEPTED, 10)));

        CompetitionStandingsResponse response = service.standings(5);

        assertThat(response.rows()).extracting(Row::userId).containsExactly(7, 8, 9);
        assertThat(response.rows()).extracting(Row::position).containsExactly(1, 2, 3);
        Row ana = response.rows().get(0);
        assertThat(ana.solved()).isEqualTo(2);
        assertThat(ana.score()).isEqualTo(300);
        assertThat(ana.penaltyMinutes()).isEqualTo(62);
        Cell a = ana.cells().get(0);
        assertThat(a.result()).isEqualTo(AssignmentStatus.RESUELTO);
        assertThat(a.attempts()).isEqualTo(2);
        assertThat(a.minutes()).isEqualTo(12);
        Cell b = ana.cells().get(1);
        assertThat(b.attempts()).isEqualTo(1);
        assertThat(b.minutes()).isEqualTo(30);
        assertThat(ana.cells().get(2).result()).isEqualTo(AssignmentStatus.SIN_INTENTOS);
        Row beto = response.rows().get(1);
        assertThat(beto.solved()).isEqualTo(1);
        assertThat(beto.penaltyMinutes()).isEqualTo(10);
    }

    @Test
    void icpcPrefersFewerPenaltyMinutesWhenSolvedCountIsEqual() {
        when(resolutions.findAttemptsByCompetition(5, 3)).thenReturn(List.of(
                attempt(1, 31, 11, Verdict.TLE, 1), attempt(2, 31, 11, Verdict.ACCEPTED, 5), // 5 + 20 = 25
                attempt(3, 32, 11, Verdict.ACCEPTED, 20),                                     // 20
                attempt(4, 33, 11, Verdict.ACCEPTED, 30)));                                   // 30

        CompetitionStandingsResponse response = service.standings(5);

        assertThat(response.rows()).extracting(Row::userId).containsExactly(8, 7, 9);
        assertThat(response.rows()).extracting(Row::penaltyMinutes).containsExactly(20, 25, 30);
        assertThat(response.rows()).extracting(Row::position).containsExactly(1, 2, 3);
    }

    @Test
    void tiesShareThePositionAndTheNextOneSkipsAheadOrderedByName() {
        when(resolutions.findAttemptsByCompetition(5, 3)).thenReturn(List.of(
                attempt(1, 33, 11, Verdict.ACCEPTED, 10), attempt(2, 31, 11, Verdict.ACCEPTED, 10),
                attempt(3, 32, 11, Verdict.WRONG_ANSWER, 3)));

        CompetitionStandingsResponse response = service.standings(5);

        assertThat(response.rows()).extracting(Row::fullName).containsExactly("Ana Pérez", "Carla Soto", "Beto Ríos");
        assertThat(response.rows()).extracting(Row::position).containsExactly(1, 1, 3);
    }

    // ---- IOI

    @Test
    void ioiRanksByScoreAndIgnoresPenalties() {
        when(competitions.findDetailById(5)).thenReturn(Optional.of(detail(PenaltyRule.IOI_POINTS)));
        when(resolutions.findAttemptsByCompetition(5, 3)).thenReturn(List.of(
                attempt(1, 31, 11, Verdict.WRONG_ANSWER, 2), attempt(2, 31, 11, Verdict.ACCEPTED, 50), // 100
                attempt(3, 32, 12, Verdict.ACCEPTED, 100),                                              // 200
                attempt(4, 33, 11, Verdict.ACCEPTED, 5)));                                              // 100

        CompetitionStandingsResponse response = service.standings(5);

        assertThat(response.penaltyRule()).isEqualTo(PenaltyRule.IOI_POINTS);
        assertThat(response.rows()).extracting(Row::fullName).containsExactly("Beto Ríos", "Ana Pérez", "Carla Soto");
        assertThat(response.rows()).extracting(Row::score).containsExactly(200, 100, 100);
        assertThat(response.rows()).extracting(Row::position).containsExactly(1, 2, 2);
        assertThat(response.rows()).extracting(Row::penaltyMinutes).containsOnly(0);
    }

    // ---- estado de cada celda

    @Test
    void derivesTheCellResultLikeTheAssignedProblemsView() {
        when(resolutions.findAttemptsByCompetition(5, 3)).thenReturn(List.of(
                attempt(1, 31, 11, Verdict.PENDIENTE, 4),                                           // por verificar
                attempt(2, 31, 12, Verdict.WRONG_ANSWER, 6), attempt(3, 31, 12, Verdict.MLE, 9),    // sin aceptar
                attempt(4, 31, 13, Verdict.PENDIENTE, 8), attempt(5, 31, 13, Verdict.ERROR, 7)));   // pendiente y fallo

        Row ana = service.standings(5).rows().stream().filter(row -> row.userId() == 7).findFirst().orElseThrow();

        assertThat(ana.cells()).extracting(Cell::result).containsExactly(AssignmentStatus.PENDIENTE,
                AssignmentStatus.EN_PROGRESO, AssignmentStatus.PENDIENTE);
        assertThat(ana.cells()).extracting(Cell::attempts).containsExactly(1, 2, 2);
        assertThat(ana.cells()).extracting(Cell::minutes).containsOnlyNulls();
        assertThat(ana.solved()).isZero();
        assertThat(ana.penaltyMinutes()).isZero();
    }

    @Test
    void aPendingAttemptBeforeTheAcceptanceCountsAsAnAttemptButNotAsAPenalty() {
        when(resolutions.findAttemptsByCompetition(5, 3)).thenReturn(List.of(
                attempt(1, 31, 11, Verdict.PENDIENTE, 3), attempt(2, 31, 11, Verdict.WRONG_ANSWER, 6),
                attempt(3, 31, 11, Verdict.ACCEPTED, 15)));

        Row ana = service.standings(5).rows().get(0);

        assertThat(ana.cells().get(0).attempts()).isEqualTo(3);
        assertThat(ana.penaltyMinutes()).isEqualTo(35); // 15 + una sola respuesta fallida
    }

    // ---- aislamiento del progreso

    @Test
    void ignoresResolutionsOfOtherTeamsAndOfOtherCompetitions() {
        when(resolutions.findAttemptsByCompetition(5, 3)).thenReturn(List.of(
                attempt(1, 99, 11, Verdict.ACCEPTED, 1),   // membresía de otro equipo
                attempt(2, 34, 11, Verdict.ACCEPTED, 2),   // integrante retirado
                attempt(3, 31, 777, Verdict.ACCEPTED, 3),  // problema de otra competencia
                attempt(4, 31, 12, Verdict.ACCEPTED, 20)));

        CompetitionStandingsResponse response = service.standings(5);

        assertThat(response.rows()).extracting(Row::userId).containsExactly(7, 8, 9);
        Row ana = response.rows().get(0);
        assertThat(ana.solved()).isEqualTo(1);
        assertThat(ana.cells().get(0).result()).isEqualTo(AssignmentStatus.SIN_INTENTOS);
        assertThat(ana.cells().get(1).result()).isEqualTo(AssignmentStatus.RESUELTO);
        assertThat(response.rows().subList(1, 3)).allSatisfy(row -> assertThat(row.solved()).isZero());
    }

    // ---- acceso

    @Test
    void anActiveMemberOfTheTeamCanSeeTheStandings() {
        when(resolver.currentUser()).thenReturn(user(7, Role.PRACTICANTE, UserStatus.ACTIVO));

        assertThat(service.standings(5).rows()).hasSize(3);
    }

    @Test
    void theOwnerCoachCanSeeTheStandings() {
        assertThat(service.standings(5).competitionId()).isEqualTo(5);
    }

    @Test
    void aPractitionerOutsideTheTeamIsForbiddenWithoutReadingResolutions() {
        when(resolver.currentUser()).thenReturn(user(40, Role.PRACTICANTE, UserStatus.ACTIVO));

        assertThatThrownBy(() -> service.standings(5)).isInstanceOf(ForbiddenException.class)
                .hasMessage("No pertenece a este equipo");
        verifyNoInteractions(resolutions, competitionProblems, problems);
    }

    @Test
    void aFormerMemberIsForbidden() {
        when(resolver.currentUser()).thenReturn(user(6, Role.PRACTICANTE, UserStatus.ACTIVO));

        assertThatThrownBy(() -> service.standings(5)).isInstanceOf(ForbiddenException.class)
                .hasMessage("No pertenece a este equipo");
    }

    @Test
    void anotherCoachIsForbidden() {
        when(resolver.currentUser()).thenReturn(user(11, Role.COACH, UserStatus.ACTIVO));

        assertThatThrownBy(() -> service.standings(5)).isInstanceOf(ForbiddenException.class)
                .hasMessage("No es el coach responsable de este equipo");
        verifyNoInteractions(resolutions, competitionProblems, problems);
    }

    @Test
    void aSuspendedAccountIsForbidden() {
        when(resolver.currentUser()).thenReturn(user(10, Role.COACH, UserStatus.SUSPENDIDO));

        assertThatThrownBy(() -> service.standings(5)).isInstanceOf(ForbiddenException.class)
                .hasMessage("La cuenta no está activa");
    }

    @Test
    void anUnknownCompetitionIsNotFound() {
        when(competitions.findDetailById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.standings(99)).isInstanceOf(NotFoundException.class)
                .hasMessage("La competencia no existe");
        verifyNoInteractions(resolutions);
    }

    @Test
    void aNonPositiveIdIsAValidationError() {
        assertThatThrownBy(() -> service.standings(0)).isInstanceOf(FieldValidationException.class);
        assertThatThrownBy(() -> service.standings(null)).isInstanceOf(FieldValidationException.class);
        verifyNoInteractions(competitions);
    }

    // ---- utilidades

    private static CompetitionDetail detail(PenaltyRule rule) {
        return new CompetitionDetail(5, 3, "Semanal 12", CompetitionStatus.EN_CURSO, rule, START, END);
    }

    private static GroupMemberData member(int membershipId, int userId, String name, MembershipStatus status) {
        return new GroupMemberData(membershipId, 3, userId, name, status);
    }

    private static CompetitionAttempt attempt(int id, int membershipId, int competitionProblemId, Verdict verdict,
                                              int minutesAfterStart) {
        return new CompetitionAttempt(id, membershipId, competitionProblemId, verdict,
                START.plusMinutes(minutesAfterStart));
    }

    private static ProblemSummary summary(int id, String title) {
        return new ProblemSummary(id, title, "https://codeforces.com/p/" + id, SourcePlatform.CODEFORCES, "CF-" + id,
                "800", 1000, 256);
    }

    private static User user(int id, Role role, UserStatus status) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setStatus(status);
        return user;
    }
}

package com.kodika.kodikalab.competitions.competitionproblem;

import com.kodika.kodikalab.common.exception.FieldConflictException;
import com.kodika.kodikalab.competitions.competition.Competition;
import com.kodika.kodikalab.competitions.competition.CompetitionAccessType;
import com.kodika.kodikalab.competitions.competition.CompetitionStatus;
import com.kodika.kodikalab.competitions.competition.PenaltyRule;
import com.kodika.kodikalab.competitions.competitionproblem.dto.NewAssignment;
import com.kodika.kodikalab.problems.problem.Problem;
import com.kodika.kodikalab.teams.studygroup.StudyGroup;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompetitionProblemServiceTests {
    static final OffsetDateTime START = OffsetDateTime.parse("2026-10-20T14:00:00-05:00");

    CompetitionProblemRepository repository;
    EntityManager entityManager;
    CompetitionProblemService service;
    Competition competitionReference;

    @BeforeEach
    void setUp() {
        repository = mock(CompetitionProblemRepository.class);
        entityManager = mock(EntityManager.class);
        service = new CompetitionProblemServiceImpl(repository, entityManager);
        competitionReference = competition(5, 1);
        when(entityManager.getReference(Competition.class, 5)).thenReturn(competitionReference);
        when(entityManager.getReference(Problem.class, 11)).thenReturn(problem(11));
        when(entityManager.getReference(Problem.class, 12)).thenReturn(problem(12));
    }

    @Test
    void assignBuildsOneRowPerProblemWithTheResolvedValuesAndReturnsThem() {
        when(repository.saveAllAndFlush(anyList())).thenAnswer(invocation -> {
            List<CompetitionProblem> rows = invocation.getArgument(0);
            int id = 40;
            for (CompetitionProblem row : rows) {
                row.setId(id++);
            }
            return rows;
        });

        var assigned = service.assign(5, List.of(new NewAssignment(11, "A", 100, "#00FF00"),
                new NewAssignment(12, "B", 1, "#FF0000")));

        ArgumentCaptor<List<CompetitionProblem>> saved = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAllAndFlush(saved.capture());
        assertThat(saved.getValue()).hasSize(2);
        assertThat(saved.getValue()).allSatisfy(row -> {
            assertThat(row.getCompetition()).isSameAs(competitionReference);
            assertThat(row.getAssignedAt()).isNotNull();
        });
        assertThat(saved.getValue().get(0).getProblem().getId()).isEqualTo(11);
        assertThat(saved.getValue().get(0).getLetter()).isEqualTo("A");
        assertThat(saved.getValue().get(0).getScore()).isEqualTo(100);
        assertThat(saved.getValue().get(0).getBalloonColor()).isEqualTo("#00FF00");
        assertThat(saved.getValue().get(0).getAssignedAt()).isEqualTo(saved.getValue().get(1).getAssignedAt());

        assertThat(assigned).extracting("competitionProblemId", "competitionId", "problemId", "letter")
                .containsExactly(org.assertj.core.groups.Tuple.tuple(40, 5, 11, "A"),
                        org.assertj.core.groups.Tuple.tuple(41, 5, 12, "B"));
    }

    @Test
    void aUniqueViolationOfTheCompetitionIsReportedAsAConflict() {
        SQLException sql = new SQLException("ERROR: duplicate key value violates unique constraint "
                + "\"uq_competencia_orden_letra\"", "23505");
        when(repository.saveAllAndFlush(anyList())).thenThrow(new DataIntegrityViolationException("dup", sql));

        assertThatThrownBy(() -> service.assign(5, List.of(new NewAssignment(11, "A", 1, "#FF0000"))))
                .isInstanceOfSatisfying(FieldConflictException.class,
                        exception -> assertThat(exception.getErrors()).containsKey("problems"));
    }

    @Test
    void anyOtherIntegrityViolationIsNotHiddenAsAConflict() {
        var failure = new DataIntegrityViolationException("null value in column", new SQLException("x", "23502"));
        when(repository.saveAllAndFlush(anyList())).thenThrow(failure);

        assertThatThrownBy(() -> service.assign(5, List.of(new NewAssignment(11, "A", 1, "#FF0000"))))
                .isSameAs(failure);
    }

    @Test
    void findByCompetitionIdMapsTheRowsInTheOrderOfTheRepository() {
        when(repository.findByCompetition_IdOrderByLetterAsc(5)).thenReturn(List.of(row(40, 11, "A"), row(41, 12, "B")));

        assertThat(service.findByCompetitionId(5)).extracting("competitionProblemId", "problemId", "letter", "score")
                .containsExactly(org.assertj.core.groups.Tuple.tuple(40, 11, "A", 100),
                        org.assertj.core.groups.Tuple.tuple(41, 12, "B", 100));
    }

    @Test
    void viewsCarryTheTeamAndTheConditionsOfTheCompetitionWithoutTheAccessKey() {
        when(repository.findWithCompetitionByTeamId(1)).thenReturn(List.of(row(40, 11, "A")));
        when(repository.findDetailById(40)).thenReturn(Optional.of(row(40, 11, "A")));
        when(repository.findDetailById(99)).thenReturn(Optional.empty());

        var views = service.findViewsByTeamId(1);

        assertThat(views).hasSize(1);
        var view = views.get(0);
        assertThat(view.competitionProblemId()).isEqualTo(40);
        assertThat(view.problemId()).isEqualTo(11);
        assertThat(view.teamId()).isEqualTo(1);
        assertThat(view.competitionId()).isEqualTo(5);
        assertThat(view.competitionName()).isEqualTo("Simulacro");
        assertThat(view.competitionStatus()).isEqualTo(CompetitionStatus.PROGRAMADA);
        assertThat(view.durationMinutes()).isEqualTo(300);
        assertThat(view.penaltyRule()).isEqualTo(PenaltyRule.ICPC_20_MIN);
        assertThat(view.accessType()).isEqualTo(CompetitionAccessType.PRIVADO_PASS);
        assertThat(java.util.Arrays.stream(view.getClass().getRecordComponents()).map(c -> c.getName()))
                .doesNotContain("accessKey");
        assertThat(service.findViewById(40)).isPresent();
        assertThat(service.findViewById(99)).isEmpty();
    }

    private CompetitionProblem row(int id, int problemId, String letter) {
        CompetitionProblem row = new CompetitionProblem();
        row.setId(id);
        row.setCompetition(competitionReference);
        row.setProblem(problem(problemId));
        row.setLetter(letter);
        row.setScore(100);
        row.setBalloonColor("#FF0000");
        row.setAssignedAt(START);
        return row;
    }

    private static Competition competition(int id, int teamId) {
        StudyGroup group = new StudyGroup();
        group.setId(teamId);
        Competition competition = new Competition();
        competition.setId(id);
        competition.setGroup(group);
        competition.setEventName("Simulacro");
        competition.setStatus(CompetitionStatus.PROGRAMADA);
        competition.setStartsAt(START);
        competition.setEndsAt(START.plusHours(5));
        competition.setDurationMinutes(300);
        competition.setPenaltyRule(PenaltyRule.ICPC_20_MIN);
        competition.setScoreboardFreezeMinutes(60);
        competition.setAccessType(CompetitionAccessType.PRIVADO_PASS);
        competition.setAccessKey("$2a$hash");
        return competition;
    }

    private static Problem problem(int id) {
        Problem problem = new Problem();
        problem.setId(id);
        return problem;
    }
}

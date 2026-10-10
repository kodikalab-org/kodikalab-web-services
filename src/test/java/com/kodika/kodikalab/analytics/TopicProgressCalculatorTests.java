package com.kodika.kodikalab.analytics;

import com.kodika.kodikalab.analytics.dto.TopicProgressResponse;
import com.kodika.kodikalab.analytics.dto.TopicProgressResponse.TopicProgress;
import com.kodika.kodikalab.analytics.dto.TopicProgressResponse.TopicStatus;
import com.kodika.kodikalab.competitions.competition.CompetitionStatus;
import com.kodika.kodikalab.competitions.competitionproblem.dto.TeamAssignedProblem;
import com.kodika.kodikalab.competitions.problemresolution.Verdict;
import com.kodika.kodikalab.competitions.problemresolution.dto.MemberAttempt;
import com.kodika.kodikalab.problems.problemtopic.dto.ProblemTopicData;
import com.kodika.kodikalab.teams.groupmembership.MembershipStatus;
import com.kodika.kodikalab.teams.groupmembership.dto.GroupMemberData;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TopicProgressCalculatorTests {
    static final int GRAFOS = 10;
    static final int PD = 20;
    static final int BFS = 30;

    TopicProgressCalculator calculator = new TopicProgressCalculator();
    GroupMemberData member = new GroupMemberData(7, 1, 10, "Ana Prueba", MembershipStatus.ACTIVO);
    // Problema 100: Grafos y BFS. Problema 101: Programación dinámica. Problema 102: Grafos.
    List<TeamAssignedProblem> assignments = List.of(assignment(1, 100), assignment(2, 101), assignment(3, 102));
    List<ProblemTopicData> topics = List.of(topic(100, GRAFOS, "Grafos"), topic(100, BFS, "BFS"),
            topic(101, PD, "Programación dinámica"), topic(102, GRAFOS, "Grafos"));

    @Test
    void groupsTheProgressByTopicWeakestFirstAndFlagsOnlyTheMinimum() {
        var result = calculate(attempt(1, 1, Verdict.ACCEPTED));

        assertThat(result.teamId()).isEqualTo(1);
        assertThat(result.membershipId()).isEqualTo(7);
        assertThat(result.userId()).isEqualTo(10);
        assertThat(result.assignedProblems()).isEqualTo(3);
        assertThat(result.solvedProblems()).isEqualTo(1);
        assertThat(result.unclassifiedProblems()).isZero();
        assertThat(result.reinforcementCriterion()).contains("menor cobertura");
        assertThat(result.topics()).extracting(TopicProgress::topicId).containsExactly(PD, GRAFOS, BFS);

        TopicProgress dp = result.topics().get(0);
        assertThat(dp.topicName()).isEqualTo("Programación dinámica");
        assertThat(dp.assignedProblems()).isEqualTo(1);
        assertThat(dp.solvedProblems()).isZero();
        assertThat(dp.unsolvedProblems()).isEqualTo(1);
        assertThat(dp.coveragePercentage()).isEqualByComparingTo("0.00");
        assertThat(dp.status()).isEqualTo(TopicStatus.SIN_ACTIVIDAD);
        assertThat(dp.needsReinforcement()).isTrue();

        TopicProgress graphs = result.topics().get(1);
        assertThat(graphs.assignedProblems()).isEqualTo(2);
        assertThat(graphs.solvedProblems()).isEqualTo(1);
        assertThat(graphs.coveragePercentage()).isEqualByComparingTo("50.00");
        assertThat(graphs.status()).isEqualTo(TopicStatus.EN_PROGRESO);
        assertThat(graphs.needsReinforcement()).isFalse();

        TopicProgress bfs = result.topics().get(2);
        assertThat(bfs.coveragePercentage()).isEqualByComparingTo("100.00");
        assertThat(bfs.status()).isEqualTo(TopicStatus.COMPLETADO);
        assertThat(bfs.needsReinforcement()).isFalse();
    }

    @Test
    void topicsWithoutActivityShowAnInitialStateAndTiedMinimumsAreAllFlagged() {
        var result = calculate();

        assertThat(result.solvedProblems()).isZero();
        assertThat(result.topics()).hasSize(3).allSatisfy(topic -> {
            assertThat(topic.status()).isEqualTo(TopicStatus.SIN_ACTIVIDAD);
            assertThat(topic.solvedProblems()).isZero();
            assertThat(topic.coveragePercentage()).isEqualByComparingTo("0");
            assertThat(topic.needsReinforcement()).isTrue();
        });
    }

    @Test
    void aTopicWithActivityIsDifferentiatedFromOneWithoutIt() {
        var result = calculate(attempt(1, 2, Verdict.WRONG_ANSWER));

        TopicProgress dp = result.topics().stream().filter(t -> t.topicId() == PD).findFirst().orElseThrow();
        assertThat(dp.status()).isEqualTo(TopicStatus.EN_PROGRESO);
        assertThat(dp.solvedProblems()).isZero();
        assertThat(dp.pendingProblems()).isZero();
        assertThat(result.topics().stream().filter(t -> t.topicId() != PD)).allSatisfy(
                t -> assertThat(t.status()).isEqualTo(TopicStatus.SIN_ACTIVIDAD));
    }

    @Test
    void pendingAttemptsAreReportedAndStopBeingPendingOnceTheProblemIsSolved() {
        var pending = calculate(attempt(1, 2, Verdict.PENDIENTE));
        TopicProgress dp = pending.topics().stream().filter(t -> t.topicId() == PD).findFirst().orElseThrow();
        assertThat(dp.pendingProblems()).isEqualTo(1);
        assertThat(dp.status()).isEqualTo(TopicStatus.EN_PROGRESO);
        assertThat(dp.solvedProblems()).isZero();

        var solved = calculate(attempt(1, 2, Verdict.PENDIENTE), attempt(2, 2, Verdict.ACCEPTED));
        dp = solved.topics().stream().filter(t -> t.topicId() == PD).findFirst().orElseThrow();
        assertThat(dp.pendingProblems()).isZero();
        assertThat(dp.solvedProblems()).isEqualTo(1);
        assertThat(dp.status()).isEqualTo(TopicStatus.COMPLETADO);
    }

    @Test
    void theSameProblemAssignedInTwoCompetitionsCountsOnce() {
        assignments = List.of(assignment(1, 100), assignment(2, 101), assignment(3, 102),
                new TeamAssignedProblem(4, 51, 1, 100, CompetitionStatus.FINALIZADA));

        var result = calculate(attempt(1, 1, Verdict.ACCEPTED), attempt(2, 4, Verdict.ACCEPTED));

        assertThat(result.assignedProblems()).isEqualTo(3);
        assertThat(result.solvedProblems()).isEqualTo(1);
        TopicProgress graphs = result.topics().stream().filter(t -> t.topicId() == GRAFOS).findFirst().orElseThrow();
        assertThat(graphs.assignedProblems()).isEqualTo(2);
        assertThat(graphs.solvedProblems()).isEqualTo(1);
    }

    @Test
    void aProblemWithSeveralTopicsCountsOnceInEachOne() {
        var result = calculate(attempt(1, 1, Verdict.ACCEPTED));

        assertThat(result.topics().stream().filter(t -> t.topicId() == GRAFOS || t.topicId() == BFS))
                .allSatisfy(topic -> assertThat(topic.solvedProblems()).isEqualTo(1));
        assertThat(result.solvedProblems()).isEqualTo(1);
    }

    @Test
    void nothingIsFlaggedWhenEveryTopicIsComplete() {
        var result = calculate(attempt(1, 1, Verdict.ACCEPTED), attempt(2, 2, Verdict.ACCEPTED),
                attempt(3, 3, Verdict.ACCEPTED));

        assertThat(result.solvedProblems()).isEqualTo(3);
        assertThat(result.topics()).allSatisfy(topic -> {
            assertThat(topic.status()).isEqualTo(TopicStatus.COMPLETADO);
            assertThat(topic.coveragePercentage()).isEqualByComparingTo("100");
            assertThat(topic.needsReinforcement()).isFalse();
        });
    }

    @Test
    void equivalentFractionsShareTheMinimumEvenWithDifferentSampleSizes() {
        List<TeamAssignedProblem> assigned = new ArrayList<>();
        List<ProblemTopicData> classification = new ArrayList<>();
        // Tema 1: 1 de 3 resueltos. Tema 2: 2 de 6 resueltos. Tema 3: 1 de 2 resueltos.
        for (int problem = 1; problem <= 3; problem++) {
            assigned.add(assignment(problem, problem));
            classification.add(topic(problem, 1, "Uno"));
        }
        for (int problem = 4; problem <= 9; problem++) {
            assigned.add(assignment(problem, problem));
            classification.add(topic(problem, 2, "Dos"));
        }
        for (int problem = 10; problem <= 11; problem++) {
            assigned.add(assignment(problem, problem));
            classification.add(topic(problem, 3, "Tres"));
        }
        assignments = assigned;
        topics = classification;

        var result = calculate(attempt(1, 1, Verdict.ACCEPTED), attempt(2, 4, Verdict.ACCEPTED),
                attempt(3, 5, Verdict.ACCEPTED), attempt(4, 10, Verdict.ACCEPTED));

        assertThat(result.topics()).extracting(TopicProgress::topicId).containsExactly(2, 1, 3);
        assertThat(result.topics()).extracting(TopicProgress::needsReinforcement).containsExactly(true, true, false);
        assertThat(result.topics().get(0).coveragePercentage()).isEqualByComparingTo("33.33");
        assertThat(result.topics().get(1).coveragePercentage()).isEqualByComparingTo("33.33");
    }

    @Test
    void aProblemWithoutTopicsIsCountedAsUnclassifiedInsteadOfBreakingTheView() {
        topics = List.of(topic(100, GRAFOS, "Grafos"), topic(101, PD, "Programación dinámica"));

        var result = calculate(attempt(1, 3, Verdict.ACCEPTED));

        assertThat(result.assignedProblems()).isEqualTo(3);
        assertThat(result.solvedProblems()).isEqualTo(1);
        assertThat(result.unclassifiedProblems()).isEqualTo(1);
        assertThat(result.topics()).extracting(TopicProgress::topicId).containsExactlyInAnyOrder(GRAFOS, PD);
    }

    @Test
    void aTeamWithoutAssignedProblemsGetsAnEmptyProgress() {
        assignments = List.of();
        topics = List.of();

        var result = calculate();

        assertThat(result.assignedProblems()).isZero();
        assertThat(result.solvedProblems()).isZero();
        assertThat(result.topics()).isEmpty();
    }

    @Test
    void anIdenticalRepeatedAttemptIsIgnoredAndTheResponseCannotBeModified() {
        var result = calculate(attempt(1, 1, Verdict.ACCEPTED), attempt(1, 1, Verdict.ACCEPTED));

        assertThat(result.solvedProblems()).isEqualTo(1);
        assertThatThrownBy(() -> result.topics().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    // ---- datos inconsistentes: se rechaza el cálculo y se informa qué dato falla ----

    @Test
    void anAttemptOnAProblemThatIsNotAssignedToTheTeamIsRejected() {
        assertInvalid("attempts[0].competitionProblemId", () -> calculate(attempt(1, 99, Verdict.ACCEPTED)));
    }

    @Test
    void anAttemptWithoutVerdictOrIdentifierIsRejected() {
        assertInvalid("attempts[0].verdict", () -> calculate(attempt(1, 1, null)));
        assertInvalid("attempts[0].resolutionId", () -> calculate(attempt(0, 1, Verdict.ACCEPTED)));
        assertInvalid("attempts[0]", () -> calculator.calculate(1, member, assignments,
                Arrays.asList((MemberAttempt) null), topics));
    }

    @Test
    void contradictoryAttemptsWithTheSameIdAreRejected() {
        assertInvalid("attempts[1].resolutionId",
                () -> calculate(attempt(1, 1, Verdict.ACCEPTED), attempt(1, 1, Verdict.WRONG_ANSWER)));
    }

    @Test
    void anAssignmentFromAnotherTeamOrWithContradictoryDataIsRejected() {
        assignments = List.of(new TeamAssignedProblem(1, 50, 2, 100, CompetitionStatus.EN_CURSO));
        assertInvalid("assignments[0].teamId", () -> calculate());

        assignments = List.of(assignment(1, 100), assignment(1, 101));
        assertInvalid("assignments[1]", () -> calculate());

        assignments = List.of(new TeamAssignedProblem(0, 50, 1, 100, CompetitionStatus.EN_CURSO));
        assertInvalid("assignments[0].competitionProblemId", () -> calculate());
    }

    @Test
    void aTopicOfAProblemOutsideTheTeamOrWithABlankOrContradictoryNameIsRejected() {
        topics = List.of(topic(999, GRAFOS, "Grafos"));
        assertInvalid("topics[0].problemId", () -> calculate());

        topics = List.of(topic(100, GRAFOS, "  "));
        assertInvalid("topics[0]", () -> calculate());

        topics = List.of(topic(100, GRAFOS, "Grafos"), topic(102, GRAFOS, "Otro nombre"));
        assertInvalid("topics[1].topicName", () -> calculate());
    }

    @Test
    void missingInputsAreRejected() {
        assertInvalid("assignments", () -> calculator.calculate(1, member, null, List.of(), topics));
        assertInvalid("attempts", () -> calculator.calculate(1, member, assignments, null, topics));
        assertInvalid("topics", () -> calculator.calculate(1, member, assignments, List.of(), null));
        assertInvalid("member", () -> calculator.calculate(1, null, assignments, List.of(), topics));
    }

    @Test
    void theMembershipMustBelongToTheTeamBeingQueried() {
        assertInvalid("member.teamId", () -> calculator.calculate(2, member, assignments, List.of(), topics));
    }

    @ParameterizedTest
    @ValueSource(strings = {"RETIRADO", "EXPULSADO", "PENDIENTE"})
    void theMembershipMustBeActive(String status) {
        GroupMemberData inactive = new GroupMemberData(7, 1, 10, "Ana", MembershipStatus.valueOf(status));
        assertInvalid("member.status", () -> calculator.calculate(1, inactive, assignments, List.of(), topics));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void theTeamIdMustBePositive(int teamId) {
        assertInvalid("teamId", () -> calculator.calculate(teamId, member, assignments, List.of(), topics));
    }

    private TopicProgressResponse calculate(MemberAttempt... attempts) {
        return calculator.calculate(1, member, assignments, List.of(attempts), topics);
    }

    private void assertInvalid(String field, Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(TopicProgressDataException.class,
                exception -> assertThat(exception.getErrors()).containsKey(field));
    }

    private static TeamAssignedProblem assignment(int competitionProblemId, int problemId) {
        return new TeamAssignedProblem(competitionProblemId, 50, 1, problemId, CompetitionStatus.EN_CURSO);
    }

    private static ProblemTopicData topic(int problemId, int topicId, String name) {
        return new ProblemTopicData(problemId, topicId, name);
    }

    private static MemberAttempt attempt(int resolutionId, int competitionProblemId, Verdict verdict) {
        return new MemberAttempt(resolutionId, competitionProblemId, verdict, "Java 21",
                OffsetDateTime.parse("2026-10-10T10:00:00-05:00"), null);
    }
}

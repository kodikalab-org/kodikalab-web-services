package com.kodika.kodikalab.analytics;

import com.kodika.kodikalab.competitions.competition.CompetitionStatus;
import com.kodika.kodikalab.competitions.competitionproblem.dto.TeamAssignedProblem;
import com.kodika.kodikalab.competitions.problemresolution.Verdict;
import com.kodika.kodikalab.competitions.problemresolution.dto.TeamResolutionData;
import com.kodika.kodikalab.problems.problemtopic.dto.ProblemTopicData;
import com.kodika.kodikalab.teams.groupmembership.MembershipStatus;
import com.kodika.kodikalab.teams.groupmembership.dto.GroupMemberData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class TopicCoverageCalculatorTests {
    TopicCoverageCalculator calculator = new TopicCoverageCalculator();
    List<GroupMemberData> members = List.of(member(1, 10, MembershipStatus.ACTIVO),
            member(2, 11, MembershipStatus.ACTIVO), member(3, 12, MembershipStatus.RETIRADO));
    List<TeamAssignedProblem> assignments = List.of(assignment(1, 100), assignment(2, 101), assignment(3, 102),
            new TeamAssignedProblem(4, 20, 1, 100, CompetitionStatus.FINALIZADA));
    List<ProblemTopicData> topics = List.of(topic(100, 10), topic(101, 10), topic(102, 20));
    List<TeamResolutionData> resolutions = List.of(resolution(1, 1, 1, 100, Verdict.ACCEPTED));

    @Test
    void calculatesCoverageIncludingTopicsWithoutAcceptancesAndPendingInformation() {
        var pending = resolution(5, 1, 2, 101, Verdict.PENDIENTE);
        var result = calculator.calculate(1, members, assignments, List.of(
                resolutions.getFirst(), resolutions.getFirst(), resolution(2, 2, 1, 100, Verdict.ACCEPTED),
                new TeamResolutionData(3, 1, 1, 4, 20, 1, 100, Verdict.ACCEPTED),
                resolution(4, 3, 2, 101, Verdict.ACCEPTED), pending, pending,
                resolution(6, 1, 3, 102, Verdict.WRONG_ANSWER)), topics);

        assertThat(result.activeMembers()).isEqualTo(2);
        assertThat(result.pendingResolutions()).isEqualTo(1);
        assertThat(result.topics()).extracting(t -> t.topicId()).containsExactly(20, 10);
        var lowest = result.topics().getFirst();
        assertThat(lowest.assignedProblems()).isEqualTo(1);
        assertThat(lowest.solvedProblems()).isZero();
        assertThat(lowest.lowestCoverage()).isTrue();
        var second = result.topics().get(1);
        assertThat(second.assignedProblems()).isEqualTo(2);
        assertThat(second.solvedProblems()).isEqualTo(1);
        assertThat(second.unsolvedProblems()).isEqualTo(1);
        assertThat(second.solvingMembers()).isEqualTo(2);
        assertThat(second.pendingResolutions()).isEqualTo(1);
        assertThat(second.coveragePercentage()).isEqualByComparingTo("50.00");
        assertThat(second.lowestCoverage()).isFalse();
        assertThat(result.comparisonExplanation()).contains("proporciones exactas");
        assertThatThrownBy(() -> result.topics().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void problemWithMultipleTopicsCountsOnceInEachTopicWithoutJoinMultiplication() {
        var classification = List.of(topic(100, 10), topic(101, 10), topic(102, 20), topic(100, 20), topic(100, 20));
        var result = calculator.calculate(1, members, assignments, resolutions, classification);

        assertThat(result.topics()).allSatisfy(t -> {
            assertThat(t.assignedProblems()).isEqualTo(2);
            assertThat(t.solvedProblems()).isEqualTo(1);
            assertThat(t.lowestCoverage()).isTrue();
        });
    }

    @Test
    void equivalentFractionsShareMinimumEvenWithDifferentSampleSizes() {
        List<TeamAssignedProblem> assigned = new ArrayList<>();
        List<ProblemTopicData> classification = new ArrayList<>();
        for (int id = 1; id <= 9; id++) {
            assigned.add(assignment(id, id));
            classification.add(topic(id, id <= 3 ? 10 : 20));
        }
        var result = calculator.calculate(1, members, assigned, List.of(
                resolution(1, 1, 1, 1, Verdict.ACCEPTED), resolution(2, 1, 4, 4, Verdict.ACCEPTED),
                resolution(3, 1, 5, 5, Verdict.ACCEPTED)), classification);

        assertThat(result.topics()).extracting(t -> t.topicId()).containsExactly(10, 20);
        assertThat(result.topics()).allSatisfy(t -> {
            assertThat(t.coveragePercentage()).isEqualByComparingTo("33.33");
            assertThat(t.lowestCoverage()).isTrue();
        });
        assertThat(result.topics().get(1).unsolvedProblems()).isEqualTo(4);
    }

    @Test
    void roundedPercentagesDoNotCreateFalseTies() {
        List<TeamAssignedProblem> assigned = new ArrayList<>();
        List<ProblemTopicData> classification = new ArrayList<>();
        for (int id = 1; id <= 400; id++) {
            assigned.add(assignment(id, id));
            classification.add(topic(id, id <= 201 ? 10 : 20));
        }
        var result = calculator.calculate(1, members, assigned, List.of(
                resolution(1, 1, 1, 1, Verdict.ACCEPTED), resolution(2, 1, 202, 202, Verdict.ACCEPTED)), classification);

        assertThat(result.topics()).allSatisfy(t -> assertThat(t.coveragePercentage()).isEqualByComparingTo("0.50"));
        assertThat(result.topics().getFirst().lowestCoverage()).isTrue();
        assertThat(result.topics().get(1).lowestCoverage()).isFalse();
    }

    @Test
    void ongoingAndPlannedCompetitionsDoNotAffectCoverageOrPendingCounts() {
        var assigned = new ArrayList<>(assignments);
        assigned.add(new TeamAssignedProblem(5, 30, 1, 103, CompetitionStatus.EN_CURSO));
        assigned.add(new TeamAssignedProblem(6, 40, 1, 104, CompetitionStatus.PROGRAMADA));
        var attempts = new ArrayList<>(resolutions);
        attempts.add(new TeamResolutionData(2, 1, 1, 5, 30, 1, 103, Verdict.PENDIENTE));
        attempts.add(new TeamResolutionData(3, 1, 1, 6, 40, 1, 104, Verdict.ACCEPTED));

        var result = calculator.calculate(1, members, assigned, attempts, topics);
        assertThat(result.pendingResolutions()).isZero();
        assertThat(result.topics()).hasSize(2);
        assertThat(result.topics().get(1).assignedProblems()).isEqualTo(2);
    }

    @Test
    void definitiveFailuresAreSufficientForKnownZeroCoverage() {
        var result = calculator.calculate(1, members, assignments,
                List.of(resolution(1, 1, 1, 100, Verdict.WRONG_ANSWER)), topics);
        assertThat(result.topics()).allSatisfy(t -> {
            assertThat(t.solvedProblems()).isZero();
            assertThat(t.lowestCoverage()).isTrue();
        });
    }

    @Test
    void onlyPendingAttemptsAreInsufficientAndTheirCountIsReported() {
        assertThatThrownBy(() -> calculator.calculate(1, members, assignments,
                List.of(resolution(1, 1, 1, 100, Verdict.PENDIENTE)), topics))
                .isInstanceOfSatisfying(TopicReportDataException.class,
                        e -> assertThat(e.getErrors().get("resolutions")).contains("pendientes: 1"));
    }

    @Test
    void missingClassificationIdentifiesAffectedProblemsAndCanBeRetried() {
        assertThatThrownBy(() -> calculator.calculate(1, members, assignments, resolutions, List.of(topic(100, 10))))
                .isInstanceOfSatisfying(TopicReportDataException.class,
                        e -> assertThat(e.getErrors().get("topics")).contains("101", "102"));
        assertThat(calculator.calculate(1, members, assignments, resolutions, topics).topics()).hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"members", "assignments", "resolutions", "topics"})
    void unavailableCollectionsAreNotTreatedAsEmpty(String field) {
        assertThatThrownBy(() -> calculator.calculate(1, field.equals("members") ? null : members,
                field.equals("assignments") ? null : assignments, field.equals("resolutions") ? null : resolutions,
                field.equals("topics") ? null : topics)).isInstanceOfSatisfying(TopicReportDataException.class,
                e -> assertThat(e.getErrors()).containsKey(field));
    }

    @Test
    void emptyOrInactivePopulationAndMissingActivityDoNotProduceConclusions() {
        assertThatThrownBy(() -> calculator.calculate(1, List.of(), assignments, resolutions, topics))
                .isInstanceOf(TopicReportDataException.class);
        assertThatThrownBy(() -> calculator.calculate(1, List.of(member(1, 10, MembershipStatus.EXPULSADO)),
                assignments, resolutions, topics)).isInstanceOf(TopicReportDataException.class);
        assertThatThrownBy(() -> calculator.calculate(1, members, List.of(), resolutions, topics))
                .isInstanceOf(TopicReportDataException.class);
        assertThatThrownBy(() -> calculator.calculate(1, members, assignments, List.of(), topics))
                .isInstanceOf(TopicReportDataException.class);
        assertThatThrownBy(() -> calculator.calculate(1, members, assignments,
                List.of(resolution(1, 3, 1, 100, Verdict.ACCEPTED)), topics)).isInstanceOf(TopicReportDataException.class);
    }

    static Stream<Arguments> invalidResolutions() {
        return Stream.of(
                Arguments.of(new TeamResolutionData(2, 1, 2, 1, 10, 1, 100, Verdict.ACCEPTED), "teamId"),
                Arguments.of(new TeamResolutionData(2, 1, 1, 1, 10, 2, 100, Verdict.ACCEPTED), "teamId"),
                Arguments.of(new TeamResolutionData(2, 99, 1, 1, 10, 1, 100, Verdict.ACCEPTED), "membershipId"),
                Arguments.of(new TeamResolutionData(2, 1, 1, 99, 10, 1, 100, Verdict.ACCEPTED), "competitionProblemId"),
                Arguments.of(new TeamResolutionData(2, 1, 1, 1, 11, 1, 100, Verdict.ACCEPTED), "competitionProblemId"),
                Arguments.of(new TeamResolutionData(2, 1, 1, 1, 10, 1, 101, Verdict.ACCEPTED), "competitionProblemId"),
                Arguments.of(new TeamResolutionData(2, 1, 1, 1, 10, 1, 100, null), "verdict"),
                Arguments.of(new TeamResolutionData(0, 1, 1, 1, 10, 1, 100, Verdict.ACCEPTED), "resolutionId"),
                Arguments.of(new TeamResolutionData(1, 1, 1, 1, 10, 1, 100, Verdict.WRONG_ANSWER), "resolutionId"));
    }

    @ParameterizedTest
    @MethodSource("invalidResolutions")
    void inconsistentResolutionsRejectTheWholeReport(TeamResolutionData resolution, String field) {
        assertThatThrownBy(() -> calculator.calculate(1, members, assignments,
                List.of(resolutions.getFirst(), resolution), topics)).isInstanceOfSatisfying(TopicReportDataException.class,
                e -> assertThat(e.getErrors()).containsKey("resolutions[1]." + field));
    }

    @Test
    void incompleteAndContradictoryInputsAreRejected() {
        assertThatThrownBy(() -> calculator.calculate(1, Arrays.asList((GroupMemberData) null), assignments,
                resolutions, topics)).isInstanceOf(TopicReportDataException.class);
        assertThatThrownBy(() -> calculator.calculate(1, List.of(members.getFirst(), members.getFirst()), assignments,
                resolutions, topics)).isInstanceOf(TopicReportDataException.class);
        assertThatThrownBy(() -> calculator.calculate(1, members, Arrays.asList((TeamAssignedProblem) null),
                resolutions, topics)).isInstanceOf(TopicReportDataException.class);
        assertThatThrownBy(() -> calculator.calculate(1, members, assignments, Arrays.asList((TeamResolutionData) null),
                topics)).isInstanceOf(TopicReportDataException.class);
        assertThatThrownBy(() -> calculator.calculate(1, members, assignments, resolutions,
                List.of(topic(100, 10), new ProblemTopicData(101, 10, "Otro nombre"))))
                .isInstanceOf(TopicReportDataException.class);
        assertThatThrownBy(() -> calculator.calculate(1, members, assignments, resolutions,
                List.of(new ProblemTopicData(100, null, "Tema Prueba")))).isInstanceOf(TopicReportDataException.class);
    }

    private static GroupMemberData member(int id, int userId, MembershipStatus status) {
        return new GroupMemberData(id, 1, userId, "Usuario Prueba", status);
    }

    private static TeamAssignedProblem assignment(int id, int problemId) {
        return new TeamAssignedProblem(id, 10, 1, problemId, CompetitionStatus.FINALIZADA);
    }

    private static ProblemTopicData topic(int problemId, int topicId) {
        return new ProblemTopicData(problemId, topicId, "Tema Prueba " + topicId);
    }

    private static TeamResolutionData resolution(int id, int membershipId, int assignedId, int problemId, Verdict verdict) {
        return new TeamResolutionData(id, membershipId, 1, assignedId, 10, 1, problemId, verdict);
    }
}

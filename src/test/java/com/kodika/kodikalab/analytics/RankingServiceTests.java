package com.kodika.kodikalab.analytics;

import com.kodika.kodikalab.analytics.dto.RankingMemberData;
import com.kodika.kodikalab.analytics.dto.RankingResolutionData;
import com.kodika.kodikalab.analytics.dto.TeamRankingResponse.MemberStanding;
import com.kodika.kodikalab.analytics.dto.TeamRankingResponse.Status;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RankingServiceTests {
    RankingService service = new RankingService();

    @Test
    void ordersByDistinctAcceptedProblemsAndIncludesActiveMembersWithZero() {
        var response = service.calculate(1, List.of(member(1), member(2), member(3)), List.of(
                resolution(1, 1, 10, "ACCEPTED"), resolution(2, 2, 10, "ACCEPTED"),
                resolution(3, 2, 20, "ACCEPTED"), resolution(4, 3, 30, "WRONG_ANSWER")));

        assertThat(response.teamId()).isEqualTo(1);
        assertThat(response.status()).isEqualTo(Status.CALCULATED);
        assertThat(response.orderingCriterion()).isEqualTo("DISTINCT_ACCEPTED_PROBLEMS_DESC");
        assertThat(response.tieCriterion()).isEqualTo("SHARED_POSITION_1_1_3");
        assertThat(response.members()).extracting(MemberStanding::membershipId).containsExactly(2, 1, 3);
        assertThat(response.members()).extracting(MemberStanding::acceptedProblems).containsExactly(2, 1, 0);
        assertThat(response.members()).extracting(MemberStanding::position).containsExactly(1, 2, 3);
        assertThat(response.members().getFirst().userId()).isEqualTo(102);
        assertThat(response.members().getFirst().fullName()).isEqualTo("Usuario Prueba 2");
    }

    @Test
    void repeatedAcceptancesOfSameProblemAcrossCompetitionsCountOnce() {
        var response = service.calculate(1, List.of(member(1)), List.of(
                resolution(1, 1, 10, "ACCEPTED"), resolution(2, 1, 10, "ACCEPTED"),
                resolution(3, 1, 20, "ACCEPTED")));

        assertThat(response.members().getFirst().acceptedProblems()).isEqualTo(2);
    }

    @Test
    void identicalDuplicateResolutionDoesNotIncreaseScore() {
        RankingResolutionData accepted = resolution(1, 1, 10, "ACCEPTED");
        var response = service.calculate(1, List.of(member(1)), List.of(accepted, accepted));

        assertThat(response.members().getFirst().acceptedProblems()).isEqualTo(1);
    }

    @Test
    void assignsSharedPositionsWithGapsAndStableDisplayOrder() {
        var response = service.calculate(1, List.of(member(3), member(2), member(1)), List.of(
                resolution(1, 1, 10, "ACCEPTED"), resolution(2, 2, 20, "ACCEPTED")));

        assertThat(response.members()).extracting(MemberStanding::membershipId).containsExactly(1, 2, 3);
        assertThat(response.members()).extracting(MemberStanding::position).containsExactly(1, 1, 3);
    }

    @Test
    void severalTieGroupsKeepCompetitionRankingPositions() {
        var response = service.calculate(1, List.of(member(1), member(2), member(3), member(4)), List.of(
                resolution(1, 1, 10, "ACCEPTED"), resolution(2, 2, 10, "ACCEPTED")));

        assertThat(response.members()).extracting(MemberStanding::position).containsExactly(1, 1, 3, 3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"RETIRADO", "EXPULSADO"})
    void inactiveMembersDoNotAppearEvenWithAcceptedProblems(String status) {
        var inactive = new RankingMemberData(2, 1, 102, "Usuario Prueba 2", status);
        var response = service.calculate(1, List.of(member(1), inactive), List.of(
                resolution(1, 1, 10, "ACCEPTED"), resolution(2, 2, 20, "ACCEPTED"),
                resolution(3, 2, 30, "ACCEPTED")));

        assertThat(response.members()).containsExactly(new MemberStanding(1, 101, "Usuario Prueba 1", 1, 1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDIENTE", "WRONG_ANSWER", "TLE", "MLE", "ERROR"})
    void recordedAttemptsWithoutAcceptancesGiveSharedZeroScores(String verdict) {
        var response = service.calculate(1, List.of(member(1), member(2)),
                List.of(resolution(1, 1, 10, verdict)));

        assertThat(response.status()).isEqualTo(Status.CALCULATED);
        assertThat(response.members()).extracting(MemberStanding::acceptedProblems).containsExactly(0, 0);
        assertThat(response.members()).extracting(MemberStanding::position).containsExactly(1, 1);
    }

    @Test
    void noRecordedActivityReturnsExplicitStatusWithoutPositions() {
        var response = service.calculate(1, List.of(member(1)), List.of());

        assertThat(response.status()).isEqualTo(Status.NO_ACTIVITY);
        assertThat(response.members()).isEmpty();
    }

    @Test
    void emptyTeamWithoutActivityHasNoPositions() {
        var response = service.calculate(1, List.of(), List.of());

        assertThat(response.status()).isEqualTo(Status.NO_ACTIVITY);
        assertThat(response.members()).isEmpty();
    }

    @Test
    void sameUserAndProblemInDifferentTeamsHaveIndependentScores() {
        var first = service.calculate(1, List.of(member(1)), List.of(resolution(1, 1, 10, "ACCEPTED")));
        var secondMember = new RankingMemberData(2, 2, 101, "Usuario Prueba 1", "ACTIVO");
        var second = service.calculate(2, List.of(secondMember), List.of(
                new RankingResolutionData(2, 2, 2, 10, "WRONG_ANSWER")));

        assertThat(first.members().getFirst().acceptedProblems()).isEqualTo(1);
        assertThat(second.members().getFirst().acceptedProblems()).isZero();
        assertThat(service.calculate(2, List.of(secondMember), List.of(
                new RankingResolutionData(3, 2, 2, 10, "ACCEPTED")))
                .members().getFirst().acceptedProblems()).isEqualTo(1);
        assertThat(first.members().getFirst().acceptedProblems()).isEqualTo(1);
    }

    @Test
    void foreignTeamResolutionRejectsWholeCalculation() {
        assertInvalid(List.of(member(1)), List.of(resolution(1, 1, 10, "ACCEPTED"),
                new RankingResolutionData(2, 2, 1, 20, "ACCEPTED")), "resolutions[1].teamId");
    }

    @Test
    void foreignTeamMembershipIsRejected() {
        assertInvalid(List.of(new RankingMemberData(1, 2, 101, "Usuario Prueba", "ACTIVO")),
                List.of(), "members[0].teamId");
    }

    @Test
    void unknownMembershipRejectsResolution() {
        assertInvalid(List.of(member(1)), List.of(resolution(1, 2, 10, "ACCEPTED")),
                "resolutions[0].membershipId");
    }

    @Test
    void conflictingDuplicateResolutionRejectsWholeCalculation() {
        assertInvalid(List.of(member(1)), List.of(resolution(1, 1, 10, "ACCEPTED"),
                resolution(1, 1, 10, "WRONG_ANSWER")), "resolutions[1].resolutionId");
    }

    @Test
    void duplicateMembershipIsRejected() {
        assertInvalid(List.of(member(1), member(1)), List.of(), "members[1]");
    }

    @Test
    void multipleMembershipsForSameUserInSameTeamAreRejected() {
        assertInvalid(List.of(member(1), new RankingMemberData(2, 1, 101, "Usuario Prueba", "ACTIVO")),
                List.of(), "members[1]");
    }

    @Test
    void missingCollectionsAreUnavailableInsteadOfEmptyActivity() {
        assertInvalid(null, List.of(), "members");
        assertInvalid(List.of(member(1)), null, "resolutions");
    }

    @Test
    void nullCollectionElementsAreRejected() {
        assertInvalid(Arrays.asList((RankingMemberData) null), List.of(), "members[0]");
        assertInvalid(List.of(member(1)), Arrays.asList((RankingResolutionData) null), "resolutions[0]");
    }

    @ParameterizedTest
    @MethodSource("invalidMembers")
    void incompleteOrInconsistentMembershipIsRejected(RankingMemberData member, String field) {
        assertInvalid(List.of(member), List.of(), "members[0]." + field);
    }

    static Stream<Arguments> invalidMembers() {
        return Stream.of(
                Arguments.of(new RankingMemberData(null, 1, 101, "Usuario Prueba", "ACTIVO"), "membershipId"),
                Arguments.of(new RankingMemberData(0, 1, 101, "Usuario Prueba", "ACTIVO"), "membershipId"),
                Arguments.of(new RankingMemberData(1, null, 101, "Usuario Prueba", "ACTIVO"), "teamId"),
                Arguments.of(new RankingMemberData(1, 1, null, "Usuario Prueba", "ACTIVO"), "userId"),
                Arguments.of(new RankingMemberData(1, 1, -1, "Usuario Prueba", "ACTIVO"), "userId"),
                Arguments.of(new RankingMemberData(1, 1, 101, null, "ACTIVO"), "fullName"),
                Arguments.of(new RankingMemberData(1, 1, 101, " ", "ACTIVO"), "fullName"),
                Arguments.of(new RankingMemberData(1, 1, 101, "Usuario Prueba", null), "status"),
                Arguments.of(new RankingMemberData(1, 1, 101, "Usuario Prueba", "UNKNOWN"), "status"));
    }

    @ParameterizedTest
    @MethodSource("invalidResolutions")
    void incompleteOrInconsistentResolutionIsRejected(RankingResolutionData resolution, String field) {
        assertInvalid(List.of(member(1)), List.of(resolution), "resolutions[0]." + field);
    }

    static Stream<Arguments> invalidResolutions() {
        return Stream.of(
                Arguments.of(new RankingResolutionData(null, 1, 1, 10, "ACCEPTED"), "resolutionId"),
                Arguments.of(new RankingResolutionData(0, 1, 1, 10, "ACCEPTED"), "resolutionId"),
                Arguments.of(new RankingResolutionData(1, null, 1, 10, "ACCEPTED"), "teamId"),
                Arguments.of(new RankingResolutionData(1, 1, null, 10, "ACCEPTED"), "membershipId"),
                Arguments.of(new RankingResolutionData(1, 1, -1, 10, "ACCEPTED"), "membershipId"),
                Arguments.of(new RankingResolutionData(1, 1, 1, null, "ACCEPTED"), "problemId"),
                Arguments.of(new RankingResolutionData(1, 1, 1, 0, "ACCEPTED"), "problemId"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"UNKNOWN", "accepted", " ACCEPTED "})
    void invalidVerdictRejectsWholeCalculation(String verdict) {
        assertInvalid(List.of(member(1)), List.of(resolution(1, 1, 10, "ACCEPTED"),
                resolution(2, 1, 20, verdict)), "resolutions[1].verdict");
    }

    @ParameterizedTest
    @MethodSource("invalidTeamIds")
    void invalidTeamIdIsRejected(Integer teamId) {
        assertThatThrownBy(() -> service.calculate(teamId, List.of(), List.of()))
                .isInstanceOfSatisfying(RankingDataException.class,
                        exception -> assertThat(exception.getErrors()).containsKey("teamId"));
    }

    static Stream<Integer> invalidTeamIds() {
        return Stream.of(null, 0, -1);
    }

    @Test
    void inputOrderDoesNotChangeRankingAndInputsRemainUntouched() {
        List<RankingMemberData> members = new ArrayList<>(List.of(member(3), member(1), member(2)));
        List<RankingResolutionData> resolutions = new ArrayList<>(List.of(
                resolution(1, 2, 10, "ACCEPTED"), resolution(2, 1, 20, "ACCEPTED")));
        List<RankingMemberData> originalMembers = List.copyOf(members);
        List<RankingResolutionData> originalResolutions = List.copyOf(resolutions);
        var first = service.calculate(1, members, resolutions);

        assertThat(members).containsExactlyElementsOf(originalMembers);
        assertThat(resolutions).containsExactlyElementsOf(originalResolutions);
        Collections.reverse(members);
        Collections.reverse(resolutions);
        assertThat(service.calculate(1, members, resolutions)).isEqualTo(first);
        assertThatThrownBy(() -> first.members().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    private void assertInvalid(List<RankingMemberData> members, List<RankingResolutionData> resolutions, String field) {
        assertThatThrownBy(() -> service.calculate(1, members, resolutions))
                .isInstanceOfSatisfying(RankingDataException.class, exception -> {
                    assertThat(exception.getMessage()).isEqualTo("No se pudo calcular un ranking válido");
                    assertThat(exception.getErrors()).containsKey(field);
                });
    }

    private RankingMemberData member(int id) {
        return new RankingMemberData(id, 1, 100 + id, "Usuario Prueba " + id, "ACTIVO");
    }

    private RankingResolutionData resolution(int id, int membershipId, int problemId, String verdict) {
        return new RankingResolutionData(id, 1, membershipId, problemId, verdict);
    }
}

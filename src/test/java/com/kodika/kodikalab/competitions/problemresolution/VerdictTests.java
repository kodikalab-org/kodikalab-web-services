package com.kodika.kodikalab.competitions.problemresolution;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class VerdictTests {
    static Stream<Arguments> codeforcesMap() {
        return Stream.of(
                Arguments.of("OK", Verdict.ACCEPTED),
                Arguments.of("WRONG_ANSWER", Verdict.WRONG_ANSWER),
                Arguments.of("TIME_LIMIT_EXCEEDED", Verdict.TLE),
                Arguments.of("MEMORY_LIMIT_EXCEEDED", Verdict.MLE),
                Arguments.of("COMPILATION_ERROR", Verdict.COMPILATION_ERROR),
                Arguments.of("RUNTIME_ERROR", Verdict.RUNTIME_ERROR),
                Arguments.of("TESTING", Verdict.EN_EVALUACION),
                Arguments.of("PRESENTATION_ERROR", Verdict.OTRO),
                Arguments.of("PARTIAL", Verdict.OTRO),
                Arguments.of("IDLENESS_LIMIT_EXCEEDED", Verdict.OTRO),
                Arguments.of("SECURITY_VIOLATED", Verdict.OTRO),
                Arguments.of("CRASHED", Verdict.OTRO),
                Arguments.of("CHALLENGED", Verdict.OTRO),
                Arguments.of("SKIPPED", Verdict.OTRO),
                Arguments.of("REJECTED", Verdict.OTRO),
                Arguments.of("FAILED", Verdict.OTRO),
                Arguments.of("SOMETHING_NEW", Verdict.OTRO));
    }

    @ParameterizedTest
    @MethodSource("codeforcesMap")
    void mapsCodeforcesVerdicts(String codeforces, Verdict expected) {
        assertThat(Verdict.fromCodeforces(codeforces)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void missingVerdictMeansStillBeingJudged(String codeforces) {
        assertThat(Verdict.fromCodeforces(codeforces)).isEqualTo(Verdict.EN_EVALUACION);
    }

    @Test
    void mappingIgnoresCaseAndSurroundingSpaces() {
        assertThat(Verdict.fromCodeforces(" ok ")).isEqualTo(Verdict.ACCEPTED);
    }

    @ParameterizedTest
    @EnumSource(value = Verdict.class, names = {"PENDIENTE", "EN_EVALUACION"})
    void pendingVerdictsNeitherSolveNorPenalize(Verdict verdict) {
        assertThat(verdict.isPending()).isTrue();
        assertThat(verdict.isFailure()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = Verdict.class, names = {"WRONG_ANSWER", "TLE", "MLE", "ERROR", "COMPILATION_ERROR",
            "RUNTIME_ERROR", "OTRO"})
    void judgedNonAcceptedVerdictsAreFailures(Verdict verdict) {
        assertThat(verdict.isFailure()).isTrue();
        assertThat(verdict.isPending()).isFalse();
    }

    @Test
    void acceptedIsNeitherPendingNorFailure() {
        assertThat(Verdict.ACCEPTED.isPending()).isFalse();
        assertThat(Verdict.ACCEPTED.isFailure()).isFalse();
    }
}

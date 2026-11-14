package com.kodika.kodikalab.assignments.dto;

import com.kodika.kodikalab.competitions.problemresolution.Verdict;
import java.time.OffsetDateTime;
import java.util.List;

/** Últimos envíos de la cuenta de Codeforces del practicante, de cualquier problema (solo lectura). */
public record PlatformSubmissionsResponse(Integer competitionProblemId, String handle, int count,
                                          List<Submission> submissions) {
    public PlatformSubmissionsResponse {
        submissions = List.copyOf(submissions);
    }

    /** {@code matchesProblem}: el envío corresponde al problema asignado consultado. */
    public record Submission(Long externalId, Integer contestId, String problemIndex, Verdict verdict,
                             String platformVerdict, String language, Integer timeMs, Integer memoryKb,
                             Integer passedTests, OffsetDateTime submittedAt, String url, boolean matchesProblem) {
    }
}

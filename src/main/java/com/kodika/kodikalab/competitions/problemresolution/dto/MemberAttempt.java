package com.kodika.kodikalab.competitions.problemresolution.dto;

import com.kodika.kodikalab.competitions.problemresolution.FileComparison;
import com.kodika.kodikalab.competitions.problemresolution.ResolutionOrigin;
import com.kodika.kodikalab.competitions.problemresolution.Verdict;
import java.time.OffsetDateTime;

/**
 * Intento de resolución de un integrante sobre un problema asignado. {@code origin} es {@code null} en filas
 * anteriores a la verificación con la plataforma (equivalen a {@code MANUAL}).
 */
public record MemberAttempt(Integer resolutionId, Integer competitionProblemId, Verdict verdict, String language,
                            OffsetDateTime submittedAt, String evidenceUrl, Integer executionTimeMs,
                            Integer memoryUsedKb, Integer passedTests, ResolutionOrigin origin,
                            Long externalSubmissionId, String fileName, FileComparison fileComparison) {
    public MemberAttempt(Integer resolutionId, Integer competitionProblemId, Verdict verdict, String language,
                         OffsetDateTime submittedAt, String evidenceUrl) {
        this(resolutionId, competitionProblemId, verdict, language, submittedAt, evidenceUrl, null, null, null, null,
                null, null, null);
    }
}

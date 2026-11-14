package com.kodika.kodikalab.competitions.problemresolution.dto;

import com.kodika.kodikalab.competitions.problemresolution.Verdict;
import java.time.OffsetDateTime;

/** Envío leído de la plataforma externa, listo para registrarse como intento. */
public record PlatformSubmissionData(Long externalId, Verdict verdict, String language, Integer executionTimeMs,
                                     Integer memoryUsedKb, Integer passedTests, OffsetDateTime submittedAt,
                                     String url) {
}

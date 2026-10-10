package com.kodika.kodikalab.competitions.problemresolution.dto;

import com.kodika.kodikalab.competitions.problemresolution.Verdict;
import java.time.OffsetDateTime;

/** Intento de resolución de un integrante sobre un problema asignado. */
public record MemberAttempt(Integer resolutionId, Integer competitionProblemId, Verdict verdict, String language,
                            OffsetDateTime submittedAt, String evidenceUrl) {
}

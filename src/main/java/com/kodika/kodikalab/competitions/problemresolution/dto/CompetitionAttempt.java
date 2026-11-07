package com.kodika.kodikalab.competitions.problemresolution.dto;

import com.kodika.kodikalab.competitions.problemresolution.Verdict;
import java.time.OffsetDateTime;

/** Intento de una membresía sobre un problema de una competencia, para armar su clasificación. */
public record CompetitionAttempt(Integer resolutionId, Integer membershipId, Integer competitionProblemId,
                                 Verdict verdict, OffsetDateTime submittedAt) {
}

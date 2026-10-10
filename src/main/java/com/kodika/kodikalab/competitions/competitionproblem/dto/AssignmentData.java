package com.kodika.kodikalab.competitions.competitionproblem.dto;

import java.time.OffsetDateTime;

/** Fila de {@code competencia_problema}: un problema asignado a una competencia. */
public record AssignmentData(Integer competitionProblemId, Integer competitionId, Integer problemId, String letter,
                             Integer score, String balloonColor, OffsetDateTime assignedAt) {
}

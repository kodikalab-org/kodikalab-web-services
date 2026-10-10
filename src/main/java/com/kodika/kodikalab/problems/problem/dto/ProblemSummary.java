package com.kodika.kodikalab.problems.problem.dto;

import com.kodika.kodikalab.problems.problem.SourcePlatform;

/** Datos del catálogo que otros módulos (asignaciones) necesitan de un problema. */
public record ProblemSummary(Integer id, String title, String url, SourcePlatform sourcePlatform, String sourceCode,
                             String difficultyRating, Integer timeLimitMs, Integer memoryLimitMb) {
}

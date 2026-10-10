package com.kodika.kodikalab.problems.problem.dto;

import com.kodika.kodikalab.problems.problem.SourcePlatform;

/** Criterios de búsqueda del catálogo; los filtros {@code null} no se aplican. */
public record ProblemSearch(String query, Integer topicId, String difficulty, SourcePlatform platform, int page,
                            int size) {
}

package com.kodika.kodikalab.recommendations;

import com.kodika.kodikalab.analytics.dto.TeamTopicReportResponse;
import java.util.List;

/**
 * Propuestas agrupadas por tema y ordenadas por dificultad. {@code minDifficulty} y {@code maxDifficulty} son el rango
 * aplicado ({@code null} si no hubo ninguno) y {@code notices} explica lo que limita el resultado.
 */
public record RecommendationResult(TeamTopicReportResponse report, List<ProblemRecommendation> problems,
                                   Integer minDifficulty, Integer maxDifficulty, List<String> notices) {
    public RecommendationResult {
        problems = List.copyOf(problems);
        notices = List.copyOf(notices);
    }
}

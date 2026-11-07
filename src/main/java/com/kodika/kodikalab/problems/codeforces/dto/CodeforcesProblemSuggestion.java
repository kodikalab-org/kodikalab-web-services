package com.kodika.kodikalab.problems.codeforces.dto;

import java.util.List;

/** Resultado de {@code GET /problems/codeforces/search}; {@code problemId} es el id local si ya fue importado. */
public record CodeforcesProblemSuggestion(String code, String title, String difficultyRating, List<String> topics,
                                          String url, Integer problemId) {
    public CodeforcesProblemSuggestion {
        topics = List.copyOf(topics);
    }
}

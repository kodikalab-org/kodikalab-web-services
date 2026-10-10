package com.kodika.kodikalab.problems.problem.dto;

import java.util.List;

public record ProblemPageResponse(List<ProblemResponse> items, int page, int size, long totalItems, int totalPages) {
    public ProblemPageResponse {
        items = List.copyOf(items);
    }
}

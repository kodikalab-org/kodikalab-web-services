package com.kodika.kodikalab.assignments.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AssignProblemsResponse(Integer competitionId, Integer teamId, List<AssignedItem> assigned) {
    public AssignProblemsResponse {
        assigned = List.copyOf(assigned);
    }

    public record AssignedItem(Integer competitionProblemId, Integer problemId, String title, String letter,
                               Integer score, String balloonColor, OffsetDateTime assignedAt) {
    }
}

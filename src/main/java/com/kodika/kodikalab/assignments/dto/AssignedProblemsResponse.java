package com.kodika.kodikalab.assignments.dto;

import java.util.List;

/** Problemas asignados de un equipo después de aplicar los filtros; {@code total} es la cantidad devuelta. */
public record AssignedProblemsResponse(Integer teamId, int total, List<AssignedProblemResponse> items) {
    public AssignedProblemsResponse {
        items = List.copyOf(items);
    }
}

package com.kodika.kodikalab.assignments.dto;

import com.kodika.kodikalab.assignments.dto.AssignedProblemResponse.AttemptInfo;
import java.util.List;

/** Detalle de una asignación: la misma información del listado más el historial de intentos propios. */
public record AssignedProblemDetailResponse(AssignedProblemResponse assignment, List<AttemptInfo> attempts) {
    public AssignedProblemDetailResponse {
        attempts = List.copyOf(attempts);
    }
}

package com.kodika.kodikalab.assignments;

import com.kodika.kodikalab.assignments.dto.AssignedProblemDetailResponse;
import com.kodika.kodikalab.assignments.dto.AssignedProblemsQuery;
import com.kodika.kodikalab.assignments.dto.AssignedProblemsResponse;

/**
 * US-08: consulta de los problemas asignados a un equipo. Solo lee: filtrar, buscar u ordenar nunca modifica una
 * asignación ni el estado del practicante.
 */
public interface AssignedProblemsService {
    /**
     * Problemas asignados del equipo. Lo consulta un practicante con membresía activa (con su estado personal) o el
     * coach responsable (sin estado personal).
     */
    AssignedProblemsResponse list(AssignedProblemsQuery query);

    /** Una asignación con su historial de intentos, bajo las mismas reglas de acceso que el listado. */
    AssignedProblemDetailResponse detail(Integer competitionProblemId);
}

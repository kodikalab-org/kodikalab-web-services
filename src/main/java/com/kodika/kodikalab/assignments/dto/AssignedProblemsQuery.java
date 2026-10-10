package com.kodika.kodikalab.assignments.dto;

import com.kodika.kodikalab.assignments.AssignmentStatus;
import com.kodika.kodikalab.competitions.competition.CompetitionStatus;

/**
 * Criterios del listado de problemas asignados; los filtros {@code null} no se aplican. {@code sort} admite
 * {@code letter}, {@code title}, {@code difficulty}, {@code assignedAt} y {@code status}; {@code order}, {@code asc}
 * o {@code desc}. Sin {@code sort} se conserva el orden por defecto: competencias más recientes primero y por letra.
 */
public record AssignedProblemsQuery(Integer teamId, Integer competitionId, CompetitionStatus competitionStatus,
                                    AssignmentStatus status, String query, String difficulty, String sort,
                                    String order) {
}

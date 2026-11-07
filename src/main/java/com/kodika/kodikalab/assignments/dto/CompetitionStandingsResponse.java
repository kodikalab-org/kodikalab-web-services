package com.kodika.kodikalab.assignments.dto;

import com.kodika.kodikalab.assignments.AssignmentStatus;
import com.kodika.kodikalab.competitions.competition.CompetitionStatus;
import com.kodika.kodikalab.competitions.competition.PenaltyRule;
import java.time.OffsetDateTime;
import java.util.List;

/** Clasificación de una competencia estilo Codeforces: una fila por integrante activo y una celda por problema. */
public record CompetitionStandingsResponse(Integer competitionId, Integer teamId, String eventName,
                                           CompetitionStatus status, PenaltyRule penaltyRule, OffsetDateTime startsAt,
                                           OffsetDateTime endsAt, List<ProblemInfo> problems, List<Row> rows) {

    /** Columna del tablero: un problema asignado a la competencia. */
    public record ProblemInfo(Integer competitionProblemId, String letter, String title, Integer score) {
    }

    /** Fila de un integrante; {@code position} se comparte entre empatados (1, 1, 3). */
    public record Row(Integer position, Integer userId, String fullName, Integer solved, Integer score,
                      Integer penaltyMinutes, List<Cell> cells) {
    }

    /**
     * Resultado de un integrante en un problema. {@code attempts} cuenta los intentos hasta el primer aceptado
     * (incluido) o todos si no lo resolvió; {@code minutes} va desde el inicio hasta ese aceptado y es nulo si no
     * lo resolvió.
     */
    public record Cell(Integer competitionProblemId, String letter, AssignmentStatus result, Integer attempts,
                       Integer minutes) {
    }
}

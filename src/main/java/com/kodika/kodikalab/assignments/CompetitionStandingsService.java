package com.kodika.kodikalab.assignments;

import com.kodika.kodikalab.assignments.dto.CompetitionStandingsResponse;

public interface CompetitionStandingsService {
    /**
     * Clasificación de una competencia, para el coach responsable del equipo o un integrante con membresía ACTIVO.
     * Solo cuenta las resoluciones de integrantes de ese equipo sobre los problemas de esa competencia.
     */
    CompetitionStandingsResponse standings(Integer competitionId);
}

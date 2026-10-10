package com.kodika.kodikalab.competitions.competition;

import com.kodika.kodikalab.competitions.competition.dto.CompetitionResponse;
import com.kodika.kodikalab.competitions.competition.dto.CompetitionSummary;
import com.kodika.kodikalab.competitions.competition.dto.CreateCompetitionRequest;
import java.util.Optional;

/** Servicio de competencias del grupo (competencia). */
public interface CompetitionService {
    /** US-13 (T1): el coach responsable de un equipo crea una competencia para ese equipo. */
    CompetitionResponse create(CreateCompetitionRequest request);

    /** Equipo y estado de la competencia, sin bloqueo. */
    Optional<CompetitionSummary> findSummaryById(Integer competitionId);

    /**
     * Igual que {@link #findSummaryById} pero bloquea la competencia hasta el fin de la transacción, para que dos
     * asignaciones simultáneas no elijan la misma letra. Exige una transacción activa.
     */
    Optional<CompetitionSummary> findSummaryForUpdate(Integer competitionId);
}

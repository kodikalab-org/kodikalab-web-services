package com.kodika.kodikalab.competitions.competition;

import com.kodika.kodikalab.competitions.competition.dto.CompetitionResponse;
import com.kodika.kodikalab.competitions.competition.dto.CreateCompetitionRequest;

/** Servicio de competencias del grupo (competencia). */
public interface CompetitionService {
    /** US-13 (T1): el coach responsable de un equipo crea una competencia para ese equipo. */
    CompetitionResponse create(CreateCompetitionRequest request);
}

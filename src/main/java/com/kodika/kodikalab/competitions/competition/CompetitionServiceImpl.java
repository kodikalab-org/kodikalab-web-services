package com.kodika.kodikalab.competitions.competition;

import org.springframework.stereotype.Service;

/** Plantilla: implementación de {@link CompetitionService} con su repositorio inyectado, sin lógica. */
@Service
public class CompetitionServiceImpl implements CompetitionService {
    private final CompetitionRepository competitionRepository;

    public CompetitionServiceImpl(CompetitionRepository competitionRepository) {
        this.competitionRepository = competitionRepository;
    }
}

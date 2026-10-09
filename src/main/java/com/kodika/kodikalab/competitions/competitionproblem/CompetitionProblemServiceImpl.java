package com.kodika.kodikalab.competitions.competitionproblem;

import org.springframework.stereotype.Service;

/** Plantilla: implementación de {@link CompetitionProblemService} con su repositorio inyectado, sin lógica. */
@Service
public class CompetitionProblemServiceImpl implements CompetitionProblemService {
    private final CompetitionProblemRepository competitionProblemRepository;

    public CompetitionProblemServiceImpl(CompetitionProblemRepository competitionProblemRepository) {
        this.competitionProblemRepository = competitionProblemRepository;
    }
}

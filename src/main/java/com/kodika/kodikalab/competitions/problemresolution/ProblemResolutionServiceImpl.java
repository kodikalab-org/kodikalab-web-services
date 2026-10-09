package com.kodika.kodikalab.competitions.problemresolution;

import org.springframework.stereotype.Service;

/** Plantilla: implementación de {@link ProblemResolutionService} con su repositorio inyectado, sin lógica. */
@Service
public class ProblemResolutionServiceImpl implements ProblemResolutionService {
    private final ProblemResolutionRepository problemResolutionRepository;

    public ProblemResolutionServiceImpl(ProblemResolutionRepository problemResolutionRepository) {
        this.problemResolutionRepository = problemResolutionRepository;
    }
}

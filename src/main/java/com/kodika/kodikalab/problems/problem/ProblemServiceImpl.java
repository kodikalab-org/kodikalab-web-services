package com.kodika.kodikalab.problems.problem;

import org.springframework.stereotype.Service;

/** Plantilla: implementación de {@link ProblemService} con su repositorio inyectado, sin lógica. */
@Service
public class ProblemServiceImpl implements ProblemService {
    private final ProblemRepository problemRepository;

    public ProblemServiceImpl(ProblemRepository problemRepository) {
        this.problemRepository = problemRepository;
    }
}

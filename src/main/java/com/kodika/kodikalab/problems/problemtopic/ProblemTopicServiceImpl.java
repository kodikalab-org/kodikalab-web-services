package com.kodika.kodikalab.problems.problemtopic;

import org.springframework.stereotype.Service;

/** Plantilla: implementación de {@link ProblemTopicService} con su repositorio inyectado, sin lógica. */
@Service
public class ProblemTopicServiceImpl implements ProblemTopicService {
    private final ProblemTopicRepository problemTopicRepository;

    public ProblemTopicServiceImpl(ProblemTopicRepository problemTopicRepository) {
        this.problemTopicRepository = problemTopicRepository;
    }
}

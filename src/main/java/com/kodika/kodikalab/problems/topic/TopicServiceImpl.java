package com.kodika.kodikalab.problems.topic;

import org.springframework.stereotype.Service;

/** Plantilla: implementación de {@link TopicService} con su repositorio inyectado, sin lógica. */
@Service
public class TopicServiceImpl implements TopicService {
    private final TopicRepository topicRepository;

    public TopicServiceImpl(TopicRepository topicRepository) {
        this.topicRepository = topicRepository;
    }
}

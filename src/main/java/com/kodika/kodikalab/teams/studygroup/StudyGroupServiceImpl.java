package com.kodika.kodikalab.teams.studygroup;

import org.springframework.stereotype.Service;

/** Plantilla: implementación de {@link StudyGroupService} con su repositorio inyectado, sin lógica. */
@Service
public class StudyGroupServiceImpl implements StudyGroupService {
    private final StudyGroupRepository studyGroupRepository;

    public StudyGroupServiceImpl(StudyGroupRepository studyGroupRepository) {
        this.studyGroupRepository = studyGroupRepository;
    }
}

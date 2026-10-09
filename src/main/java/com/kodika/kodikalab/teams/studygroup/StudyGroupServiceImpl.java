package com.kodika.kodikalab.teams.studygroup;

import com.kodika.kodikalab.teams.studygroup.dto.StudyGroupSummary;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudyGroupServiceImpl implements StudyGroupService {
    private final StudyGroupRepository studyGroupRepository;

    public StudyGroupServiceImpl(StudyGroupRepository studyGroupRepository) {
        this.studyGroupRepository = studyGroupRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StudyGroupSummary> findSummaryById(Integer teamId) {
        return studyGroupRepository.findSummaryById(teamId);
    }
}

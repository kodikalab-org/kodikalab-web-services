package com.kodika.kodikalab.teams.studygroup;

import com.kodika.kodikalab.teams.studygroup.dto.StudyGroupSummary;
import java.util.Optional;

public interface StudyGroupService {
    Optional<StudyGroupSummary> findSummaryById(Integer teamId);
}

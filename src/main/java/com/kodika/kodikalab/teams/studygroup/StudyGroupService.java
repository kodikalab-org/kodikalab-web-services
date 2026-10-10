package com.kodika.kodikalab.teams.studygroup;

import com.kodika.kodikalab.teams.studygroup.dto.StudyGroupSummary;
import java.util.List;
import java.util.Optional;

public interface StudyGroupService {

    StudyGroup createGroup(CreateStudyGroupRequest request);

    List<StudyGroup> getAvailableGroups();

    Optional<StudyGroupSummary> findSummaryById(Integer teamId);
}

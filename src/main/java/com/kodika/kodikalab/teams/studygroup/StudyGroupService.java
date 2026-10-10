package com.kodika.kodikalab.teams.studygroup;

import java.util.List;

public interface StudyGroupService {

    StudyGroup createGroup(CreateStudyGroupRequest request);

    List<StudyGroup> getAvailableGroups();
}
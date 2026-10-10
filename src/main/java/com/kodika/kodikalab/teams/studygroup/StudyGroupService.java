package com.kodika.kodikalab.teams.studygroup;

import com.kodika.kodikalab.teams.studygroup.dto.MyTeamResponse;
import com.kodika.kodikalab.teams.studygroup.dto.StudyGroupSummary;
import java.util.List;
import java.util.Optional;

public interface StudyGroupService {

    StudyGroup createGroup(CreateStudyGroupRequest request);

    List<StudyGroup> getAvailableGroups();

    /** Equipos del usuario autenticado: los grupos del coach o las membresías del practicante. */
    List<MyTeamResponse> findMyTeams();

    Optional<StudyGroupSummary> findSummaryById(Integer teamId);
}

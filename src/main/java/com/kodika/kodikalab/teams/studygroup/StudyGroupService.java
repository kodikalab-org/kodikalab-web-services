package com.kodika.kodikalab.teams.studygroup;

import com.kodika.kodikalab.teams.studygroup.dto.StudyGroupSummary;
import java.util.Optional;
import java.util.List;
import com.kodika.kodikalab.teams.studygroup.dto.MyTeamResponse;

public interface StudyGroupService {
    List<MyTeamResponse> findMyTeams();

    void requireAccessibleTeam(Integer teamId);

    Optional<StudyGroupSummary> findSummaryById(Integer teamId);
}

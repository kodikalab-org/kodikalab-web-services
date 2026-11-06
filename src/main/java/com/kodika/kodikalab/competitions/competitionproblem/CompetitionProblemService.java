package com.kodika.kodikalab.competitions.competitionproblem;

import com.kodika.kodikalab.competitions.competitionproblem.dto.TeamAssignedProblem;
import java.util.List;
import com.kodika.kodikalab.competitions.competitionproblem.dto.AssignedProblemResponse;

public interface CompetitionProblemService {
    List<AssignedProblemResponse> listByTeamId(Integer teamId);

    List<TeamAssignedProblem> findAssignedProblemsByTeamId(Integer teamId);
}

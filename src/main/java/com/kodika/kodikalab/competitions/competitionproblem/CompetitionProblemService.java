package com.kodika.kodikalab.competitions.competitionproblem;

import com.kodika.kodikalab.competitions.competitionproblem.dto.TeamAssignedProblem;
import java.util.List;

public interface CompetitionProblemService {
    List<TeamAssignedProblem> findAssignedProblemsByTeamId(Integer teamId);
}

package com.kodika.kodikalab.competitions.problemresolution;

import com.kodika.kodikalab.competitions.problemresolution.dto.TeamResolutionData;
import com.kodika.kodikalab.competitions.problemresolution.dto.ManualResolutionRequest;
import java.util.List;

public interface ProblemResolutionService {
    List<TeamResolutionData> findResolutionsByTeamId(Integer teamId);

    TeamResolutionData registerManualAccepted(Integer teamId, Integer competitionProblemId,
                                              ManualResolutionRequest request);
}

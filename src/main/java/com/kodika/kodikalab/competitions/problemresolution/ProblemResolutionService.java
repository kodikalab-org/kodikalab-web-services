package com.kodika.kodikalab.competitions.problemresolution;

import com.kodika.kodikalab.competitions.problemresolution.dto.TeamResolutionData;
import java.util.List;

public interface ProblemResolutionService {
    List<TeamResolutionData> findResolutionsByTeamId(Integer teamId);
}

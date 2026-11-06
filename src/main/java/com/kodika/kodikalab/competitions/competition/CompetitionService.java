package com.kodika.kodikalab.competitions.competition;

import com.kodika.kodikalab.competitions.competition.dto.TeamCompetitionResponse;
import java.util.List;

public interface CompetitionService {
    List<TeamCompetitionResponse> findByTeamId(Integer teamId);
}

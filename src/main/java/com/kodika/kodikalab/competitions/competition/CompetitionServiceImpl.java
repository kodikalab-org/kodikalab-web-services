package com.kodika.kodikalab.competitions.competition;

import com.kodika.kodikalab.competitions.competition.dto.TeamCompetitionResponse;
import com.kodika.kodikalab.teams.studygroup.StudyGroupService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompetitionServiceImpl implements CompetitionService {
    private final CompetitionRepository competitionRepository;
    private final StudyGroupService groups;

    public CompetitionServiceImpl(CompetitionRepository competitionRepository, StudyGroupService groups) {
        this.competitionRepository = competitionRepository;
        this.groups = groups;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeamCompetitionResponse> findByTeamId(Integer teamId) {
        groups.requireAccessibleTeam(teamId);
        return competitionRepository.findByGroupIdOrderByStartsAtDescIdDesc(teamId).stream()
                .map(competition -> new TeamCompetitionResponse(competition.getId(), teamId, competition.getEventName(),
                        competition.getStatus(), competition.getStartsAt(), competition.getEndsAt())).toList();
    }
}

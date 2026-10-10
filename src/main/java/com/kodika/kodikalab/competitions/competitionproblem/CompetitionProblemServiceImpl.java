package com.kodika.kodikalab.competitions.competitionproblem;

import com.kodika.kodikalab.competitions.competitionproblem.dto.TeamAssignedProblem;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompetitionProblemServiceImpl implements CompetitionProblemService {
    private final CompetitionProblemRepository competitionProblemRepository;

    public CompetitionProblemServiceImpl(CompetitionProblemRepository competitionProblemRepository) {
        this.competitionProblemRepository = competitionProblemRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeamAssignedProblem> findAssignedProblemsByTeamId(Integer teamId) {
        return competitionProblemRepository.findAssignedProblemsByTeamId(teamId);
    }
}

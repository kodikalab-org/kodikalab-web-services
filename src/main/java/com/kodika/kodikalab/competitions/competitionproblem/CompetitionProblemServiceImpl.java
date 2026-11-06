package com.kodika.kodikalab.competitions.competitionproblem;

import com.kodika.kodikalab.competitions.competitionproblem.dto.TeamAssignedProblem;
import java.util.List;
import com.kodika.kodikalab.competitions.competitionproblem.dto.AssignedProblemResponse;
import com.kodika.kodikalab.teams.studygroup.StudyGroupService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompetitionProblemServiceImpl implements CompetitionProblemService {
    private final CompetitionProblemRepository competitionProblemRepository;

    private final StudyGroupService groups;

    public CompetitionProblemServiceImpl(CompetitionProblemRepository competitionProblemRepository, StudyGroupService groups) {
        this.competitionProblemRepository = competitionProblemRepository;
        this.groups = groups;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignedProblemResponse> listByTeamId(Integer teamId) {
        groups.requireAccessibleTeam(teamId);
        return competitionProblemRepository.findAvailableByTeamId(teamId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeamAssignedProblem> findAssignedProblemsByTeamId(Integer teamId) {
        return competitionProblemRepository.findAssignedProblemsByTeamId(teamId);
    }
}

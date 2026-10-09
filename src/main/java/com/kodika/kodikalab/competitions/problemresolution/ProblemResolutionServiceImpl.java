package com.kodika.kodikalab.competitions.problemresolution;

import com.kodika.kodikalab.competitions.problemresolution.dto.TeamResolutionData;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProblemResolutionServiceImpl implements ProblemResolutionService {
    private final ProblemResolutionRepository problemResolutionRepository;

    public ProblemResolutionServiceImpl(ProblemResolutionRepository problemResolutionRepository) {
        this.problemResolutionRepository = problemResolutionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeamResolutionData> findResolutionsByTeamId(Integer teamId) {
        return problemResolutionRepository.findResolutionsByTeamId(teamId);
    }
}

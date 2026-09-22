package com.kodika.kodikalab.service.impl;

import com.kodika.kodikalab.dto.ProblemResponse;
import com.kodika.kodikalab.dto.AssignProblemRequest;
import com.kodika.kodikalab.dto.SubmitSolutionRequest;
import java.util.List;
import com.kodika.kodikalab.service.ProblemService;
import org.springframework.stereotype.Service;

@Service
public class ProblemServiceImpl implements ProblemService {

    @Override
    public List<ProblemResponse> assignProblems(AssignProblemRequest request) {
        return null;
    }
    @Override
    public List<ProblemResponse> getAssignedProblems() {
        return null;
    }
    @Override
    public Void submitSolution(Long problemId, SubmitSolutionRequest request) {
        throw new UnsupportedOperationException("TODO: Implementar en Sprint 1");
    }
    @Override
    public List<ProblemResponse> getResources(Long problemId) {
        return null;
    }
}

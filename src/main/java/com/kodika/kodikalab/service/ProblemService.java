package com.kodika.kodikalab.service;

import com.kodika.kodikalab.dto.ProblemResponse;
import com.kodika.kodikalab.dto.AssignProblemRequest;
import com.kodika.kodikalab.dto.SubmitSolutionRequest;
import java.util.List;

public interface ProblemService {

    List<ProblemResponse> assignProblems(AssignProblemRequest request);

    List<ProblemResponse> getAssignedProblems();

    Void submitSolution(Long problemId, SubmitSolutionRequest request);

    List<ProblemResponse> getResources(Long problemId);
}

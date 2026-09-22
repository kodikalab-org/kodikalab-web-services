package com.kodika.kodikalab.controller;

import com.kodika.kodikalab.dto.AssignProblemRequest;
import com.kodika.kodikalab.dto.ProblemResponse;
import com.kodika.kodikalab.dto.SubmitSolutionRequest;
import com.kodika.kodikalab.service.ProblemService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/problems")
public class ProblemController {

    private final ProblemService problemService;

    public ProblemController(ProblemService problemService) {
        this.problemService = problemService;
    }

    @PostMapping("/assign")
    public ResponseEntity<List<ProblemResponse>> assignProblems(@RequestBody AssignProblemRequest request) {
        return ResponseEntity.ok(problemService.assignProblems(request));
    }

    @GetMapping("/assigned")
    public ResponseEntity<List<ProblemResponse>> getAssignedProblems() {
        return ResponseEntity.ok(problemService.getAssignedProblems());
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<Void> submitSolution(@PathVariable Long id, @RequestBody SubmitSolutionRequest request) {
        problemService.submitSolution(id, request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/resources")
    public ResponseEntity<List<ProblemResponse>> getResources(@PathVariable Long id) {
        return ResponseEntity.ok(problemService.getResources(id));
    }
}

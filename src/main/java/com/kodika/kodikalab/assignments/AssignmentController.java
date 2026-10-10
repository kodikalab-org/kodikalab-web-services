package com.kodika.kodikalab.assignments;

import com.kodika.kodikalab.assignments.dto.AssignProblemsRequest;
import com.kodika.kodikalab.assignments.dto.AssignProblemsResponse;
import com.kodika.kodikalab.assignments.dto.AssignedProblemDetailResponse;
import com.kodika.kodikalab.assignments.dto.AssignedProblemsQuery;
import com.kodika.kodikalab.assignments.dto.AssignedProblemsResponse;
import com.kodika.kodikalab.competitions.competition.CompetitionStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Rutas de asignación de problemas (US-07) y de problemas asignados (US-08), bajo {@code /api/problems}. */
@RestController
@RequestMapping("/problems")
@Tag(name = "Asignaciones", description = "Asignación de problemas a competencias y vista de problemas asignados (US-07, US-08).")
public class AssignmentController {
    private final AssignmentService assignmentService;
    private final AssignedProblemsService assignedProblemsService;

    public AssignmentController(AssignmentService assignmentService, AssignedProblemsService assignedProblemsService) {
        this.assignmentService = assignmentService;
        this.assignedProblemsService = assignedProblemsService;
    }

    @PostMapping("/assign")
    @ResponseStatus(HttpStatus.CREATED)
    public AssignProblemsResponse assign(@RequestBody AssignProblemsRequest request) {
        return assignmentService.assign(request);
    }

    @GetMapping("/assigned")
    public AssignedProblemsResponse list(@RequestParam(required = false) Integer teamId,
                                         @RequestParam(required = false) Integer competitionId,
                                         @RequestParam(required = false) CompetitionStatus competitionStatus,
                                         @RequestParam(required = false) AssignmentStatus status,
                                         @RequestParam(name = "q", required = false) String query,
                                         @RequestParam(required = false) String difficulty,
                                         @RequestParam(required = false) String sort,
                                         @RequestParam(required = false) String order) {
        return assignedProblemsService.list(new AssignedProblemsQuery(teamId, competitionId, competitionStatus, status,
                query, difficulty, sort, order));
    }

    @GetMapping("/assigned/{competitionProblemId}")
    public AssignedProblemDetailResponse detail(@PathVariable Integer competitionProblemId) {
        return assignedProblemsService.detail(competitionProblemId);
    }
}

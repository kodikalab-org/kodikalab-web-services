package com.kodika.kodikalab.competitions;

import com.kodika.kodikalab.competitions.category.CategoryService;
import com.kodika.kodikalab.competitions.competition.CompetitionRepository;
import com.kodika.kodikalab.competitions.competition.CompetitionService;
import com.kodika.kodikalab.competitions.competitionproblem.CompetitionProblem;
import com.kodika.kodikalab.competitions.competitionproblem.CompetitionProblemRepository;
import com.kodika.kodikalab.competitions.competitionproblem.CompetitionProblemService;
import com.kodika.kodikalab.competitions.problemresolution.ProblemResolution;
import com.kodika.kodikalab.competitions.problemresolution.ProblemResolutionRepository;
import com.kodika.kodikalab.competitions.problemresolution.ProblemResolutionService;
import com.kodika.kodikalab.competitions.problemresolution.Verdict;
import com.kodika.kodikalab.problems.problem.ProblemRepository;
import com.kodika.kodikalab.teams.groupmembership.GroupMembershipRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Plantilla del controller de competitions en {@code /api/competitions}. Aún no expone endpoints:
 * agregar cada {@code @GetMapping}/{@code @PostMapping}... con su historia y su contrato en
 * {@code docs/sdd/03-api-contracts.md}, delegando en el servicio de la entidad.
 */
@RestController
@RequestMapping("/competitions")
public class CompetitionController {
    private final CompetitionRepository competitionRepository;
    private final CompetitionProblemRepository competitionProblemRepository;
    private final ProblemResolutionRepository resolutionRepository;
    private final ProblemRepository problemRepository;
    private final GroupMembershipRepository membershipRepository;

    public CompetitionController(CompetitionRepository competitionRepository,
                                 CompetitionProblemRepository competitionProblemRepository,
                                 ProblemResolutionRepository resolutionRepository,
                                 ProblemRepository problemRepository,
                                 GroupMembershipRepository membershipRepository) {
        this.competitionRepository = competitionRepository;
        this.competitionProblemRepository = competitionProblemRepository;
        this.resolutionRepository = resolutionRepository;
        this.problemRepository = problemRepository;
        this.membershipRepository = membershipRepository;
    }

    @GetMapping("/groups/{groupId}/problems")
    public List<Object[]> listGroupProblems(@PathVariable Integer groupId) {
        return competitionProblemRepository.listProblemsByGroupNative(groupId);
    }

    @GetMapping("/{competitionId}/problems")
    public List<CompetitionProblem> listCompetitionProblems(@PathVariable Integer competitionId) {
        return competitionProblemRepository.findByCompetition_IdOrderByLetterAsc(competitionId);
    }

    public record AssignmentRequest(Integer problemId, String letter, Integer score) {}

    @PostMapping("/{competitionId}/problems")
    @Transactional
    public ResponseEntity<?> assignProblem(@PathVariable Integer competitionId,
                                           @RequestBody AssignmentRequest request) {
        var competition = competitionRepository.findById(competitionId);
        var problem = problemRepository.findById(request.problemId());
        if (competition.isEmpty() || problem.isEmpty()) return ResponseEntity.notFound().build();
        if (competitionProblemRepository.existsByCompetition_IdAndProblem_Id(competitionId, request.problemId()))
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "El problema ya pertenece a esta competencia."));
        CompetitionProblem cp = new CompetitionProblem();
        cp.setCompetition(competition.get());
        cp.setProblem(problem.get());
        cp.setLetter(request.letter());
        cp.setScore(request.score() == null ? 1 : request.score());
        cp.setAssignedAt(OffsetDateTime.now());
        return ResponseEntity.status(HttpStatus.CREATED).body(competitionProblemRepository.save(cp));
    }

    @PutMapping("/problems/{assignmentId}")
    public ResponseEntity<?> updateAssignment(@PathVariable Integer assignmentId,
                                              @RequestBody AssignmentRequest request) {
        return competitionProblemRepository.findById(assignmentId).map(cp -> {
            if (request.letter() != null) cp.setLetter(request.letter());
            if (request.score() != null) cp.setScore(request.score());
            return ResponseEntity.ok(competitionProblemRepository.save(cp));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/problems/{assignmentId}")
    public ResponseEntity<Void> deleteAssignment(@PathVariable Integer assignmentId) {
        if (!competitionProblemRepository.existsById(assignmentId)) return ResponseEntity.notFound().build();
        competitionProblemRepository.deleteById(assignmentId);
        return ResponseEntity.noContent().build();
    }

    public record ResolutionRequest(Integer membershipId, Integer competitionProblemId, Verdict verdict,
                                    String language, Integer executionTimeMs, Integer memoryUsedKb,
                                    String evidenceUrl) {}

    @GetMapping("/resolutions")
    public List<ProblemResolution> listResolutions(@RequestParam Integer membershipId,
                                                   @RequestParam(required = false) Integer problemId) {
        if (problemId != null)
            return resolutionRepository.findByMembership_IdAndCompetitionProblem_Problem_IdOrderBySubmittedAtDesc(membershipId, problemId);
        return resolutionRepository.findByMembership_IdOrderBySubmittedAtDesc(membershipId);
    }

    @GetMapping("/resolutions/status")
    public List<Object[]> listResolutionStatuses(@RequestParam Integer membershipId) {
        return resolutionRepository.listProblemStatusNative(membershipId);
    }

    @GetMapping("/resolutions/{resolutionId}")
    public ResponseEntity<ProblemResolution> getResolution(@PathVariable Integer resolutionId) {
        return resolutionRepository.findById(resolutionId).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/resolutions")
    @Transactional
    public ResponseEntity<?> createResolution(@RequestBody ResolutionRequest request) {
        var membership = membershipRepository.findById(request.membershipId());
        var assignment = competitionProblemRepository.findById(request.competitionProblemId());
        if (membership.isEmpty() || assignment.isEmpty()) return ResponseEntity.notFound().build();
        if (!membership.get().getGroup().getId().equals(assignment.get().getCompetition().getGroup().getId()))
            return ResponseEntity.badRequest().body(Map.of("message", "El integrante y el problema deben pertenecer al mismo grupo."));
        ProblemResolution resolution = new ProblemResolution();
        resolution.setMembership(membership.get());
        resolution.setCompetitionProblem(assignment.get());
        resolution.setVerdict(request.verdict() == null ? Verdict.PENDIENTE : request.verdict());
        resolution.setLanguage(request.language() == null ? "C++20" : request.language());
        resolution.setExecutionTimeMs(request.executionTimeMs() == null ? 0 : request.executionTimeMs());
        resolution.setMemoryUsedKb(request.memoryUsedKb() == null ? 0 : request.memoryUsedKb());
        resolution.setEvidenceUrl(request.evidenceUrl());
        resolution.setSubmittedAt(OffsetDateTime.now());
        return ResponseEntity.status(HttpStatus.CREATED).body(resolutionRepository.save(resolution));
    }

    @PatchMapping("/resolutions/{resolutionId}/verdict")
    public ResponseEntity<?> updateVerdict(@PathVariable Integer resolutionId, @RequestParam Verdict verdict) {
        return resolutionRepository.findById(resolutionId).map(resolution -> {
            resolution.setVerdict(verdict);
            return ResponseEntity.ok(resolutionRepository.save(resolution));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/resolutions/{resolutionId}")
    public ResponseEntity<Void> deleteResolution(@PathVariable Integer resolutionId) {
        if (!resolutionRepository.existsById(resolutionId)) return ResponseEntity.notFound().build();
        resolutionRepository.deleteById(resolutionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/practice/continue")
    public List<ProblemResolution> continuePractice(@RequestParam Integer membershipId,
                                                    @RequestParam Integer problemId) {
        return resolutionRepository.findByMembership_IdAndCompetitionProblem_Problem_IdOrderBySubmittedAtDesc(membershipId, problemId);
    }
}

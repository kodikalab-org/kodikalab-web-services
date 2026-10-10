package com.kodika.kodikalab.competitions;

import com.kodika.kodikalab.competitions.category.CategoryService;
import com.kodika.kodikalab.competitions.competition.CompetitionService;
import com.kodika.kodikalab.competitions.competition.dto.ChangeCompetitionStatusRequest;
import com.kodika.kodikalab.competitions.competition.dto.CompetitionListResponse;
import com.kodika.kodikalab.competitions.competition.dto.CompetitionResponse;
import com.kodika.kodikalab.competitions.competition.dto.CreateCompetitionRequest;
import com.kodika.kodikalab.competitions.competitionproblem.CompetitionProblemService;
import com.kodika.kodikalab.competitions.problemresolution.ProblemResolutionService;
import com.kodika.kodikalab.competitions.officialresult.OfficialResultService;
import com.kodika.kodikalab.competitions.officialresult.dto.OfficialResultRequest;
import com.kodika.kodikalab.competitions.officialresult.dto.OfficialResultResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/competitions")
@Tag(name = "Competencias", description = "Competencias y resultados oficiales (US-13).")
public class CompetitionController {
    private final CompetitionService competitionService;
    private final CompetitionProblemService competitionProblemService;
    private final ProblemResolutionService problemResolutionService;
    private final CategoryService categoryService;
    private final OfficialResultService officialResultService;

    public CompetitionController(CompetitionService competitionService,
                                 CompetitionProblemService competitionProblemService,
                                 ProblemResolutionService problemResolutionService,
                                 CategoryService categoryService,
                                 OfficialResultService officialResultService) {
        this.competitionService = competitionService;
        this.competitionProblemService = competitionProblemService;
        this.problemResolutionService = problemResolutionService;
        this.categoryService = categoryService;
        this.officialResultService = officialResultService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompetitionResponse create(@RequestBody CreateCompetitionRequest request) {
        return competitionService.create(request);
    }

    @GetMapping
    public CompetitionListResponse list(@RequestParam(required = false) Integer teamId) {
        return competitionService.listByTeam(teamId);
    }

    @PatchMapping("/{competitionId}/status")
    public CompetitionResponse changeStatus(@PathVariable Integer competitionId,
                                            @RequestBody ChangeCompetitionStatusRequest request) {
        return competitionService.changeStatus(competitionId, request);
    }

    @PostMapping("/{competitionId}/official-result")
    @ResponseStatus(HttpStatus.CREATED)
    public OfficialResultResponse createResult(@PathVariable Integer competitionId,
                                               @RequestBody OfficialResultRequest request) {
        return officialResultService.create(competitionId, request);
    }

    @PutMapping("/{competitionId}/official-result")
    public OfficialResultResponse updateResult(@PathVariable Integer competitionId,
                                               @RequestBody OfficialResultRequest request) {
        return officialResultService.update(competitionId, request);
    }

    @GetMapping("/{competitionId}/official-result")
    public OfficialResultResponse getResult(@PathVariable Integer competitionId) {
        return officialResultService.get(competitionId);
    }

    @GetMapping("/teams/{teamId}/official-results")
    public List<OfficialResultResponse> history(@PathVariable Integer teamId) {
        return officialResultService.history(teamId);
    }
}

package com.kodika.kodikalab.competitions;

import com.kodika.kodikalab.competitions.category.CategoryService;
import com.kodika.kodikalab.competitions.competition.CompetitionService;
import com.kodika.kodikalab.competitions.competitionproblem.CompetitionProblemService;
import com.kodika.kodikalab.competitions.problemresolution.ProblemResolutionService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Plantilla del controller de competitions en {@code /api/competitions}. Aún no expone endpoints:
 * agregar cada {@code @GetMapping}/{@code @PostMapping}... con su historia y su contrato en
 * {@code docs/sdd/03-api-contracts.md}, delegando en el servicio de la entidad.
 */
@RestController
@RequestMapping("/competitions")
public class CompetitionController {
    private final CompetitionService competitionService;
    private final CompetitionProblemService competitionProblemService;
    private final ProblemResolutionService problemResolutionService;
    private final CategoryService categoryService;

    public CompetitionController(CompetitionService competitionService,
                                 CompetitionProblemService competitionProblemService,
                                 ProblemResolutionService problemResolutionService,
                                 CategoryService categoryService) {
        this.competitionService = competitionService;
        this.competitionProblemService = competitionProblemService;
        this.problemResolutionService = problemResolutionService;
        this.categoryService = categoryService;
    }
}

package com.kodika.kodikalab.assignments;

import com.kodika.kodikalab.assignments.dto.CompetitionStandingsResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Clasificación por concurso, bajo {@code /api/competitions/{competitionId}/standings}. */
@RestController
@RequestMapping("/competitions")
@Tag(name = "Asignaciones", description = "Asignación de problemas a competencias y vista de problemas asignados (US-07, US-08).")
public class CompetitionStandingsController {
    private final CompetitionStandingsService standingsService;

    public CompetitionStandingsController(CompetitionStandingsService standingsService) {
        this.standingsService = standingsService;
    }

    @GetMapping("/{competitionId}/standings")
    public CompetitionStandingsResponse standings(@PathVariable Integer competitionId) {
        return standingsService.standings(competitionId);
    }
}

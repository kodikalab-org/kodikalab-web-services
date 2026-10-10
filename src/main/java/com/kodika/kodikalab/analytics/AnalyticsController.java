package com.kodika.kodikalab.analytics;

import com.kodika.kodikalab.analytics.dto.TeamRankingResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/analytics")
@Tag(name = "Analítica", description = "Ranking, progreso y reportes por equipo (US-10, US-11, US-12, US-14).")
public class AnalyticsController {
    private final StoredTeamRankingService rankingService;

    public AnalyticsController(StoredTeamRankingService rankingService) {
        this.rankingService = rankingService;
    }

    @GetMapping("/teams/{teamId}/standings")
    public ResponseEntity<TeamRankingResponse> getStandings(@PathVariable Integer teamId) {
        return ResponseEntity.ok(rankingService.getRanking(teamId));
    }
}

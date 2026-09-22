package com.kodika.kodikalab.controller;

import com.kodika.kodikalab.dto.ProgressResponse;
import com.kodika.kodikalab.dto.StandingDto;
import com.kodika.kodikalab.dto.WeaknessReportDto;
import com.kodika.kodikalab.service.AnalyticsService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/teams/{teamId}/topics")
    public ResponseEntity<List<ProgressResponse>> getProgressByTopic(@PathVariable Long teamId) {
        return ResponseEntity.ok(analyticsService.getProgressByTopic(teamId));
    }

    @GetMapping("/teams/{teamId}/standings")
    public ResponseEntity<List<StandingDto>> getTeamRanking(@PathVariable Long teamId) {
        return ResponseEntity.ok(analyticsService.getTeamRanking(teamId));
    }

    @GetMapping("/teams/{teamId}/weaknesses")
    public ResponseEntity<WeaknessReportDto> getWeaknesses(@PathVariable Long teamId) {
        return ResponseEntity.ok(analyticsService.getWeaknesses(teamId));
    }

    @PostMapping("/teams/{teamId}/competitions")
    public ResponseEntity<Void> registerCompetition(@PathVariable Long teamId) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("/users/me/independent-progress")
    public ResponseEntity<ProgressResponse> getIndependentProgress() {
        return ResponseEntity.ok(analyticsService.getIndependentProgress());
    }
}

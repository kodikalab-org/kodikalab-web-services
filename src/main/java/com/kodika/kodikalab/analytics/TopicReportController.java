package com.kodika.kodikalab.analytics;

import com.kodika.kodikalab.analytics.dto.TeamTopicReportResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/analytics")
@Tag(name = "Analítica", description = "Ranking, progreso y reportes por equipo (US-10, US-11, US-12, US-14).")
public class TopicReportController {
    private final TeamTopicReportService service;

    public TopicReportController(TeamTopicReportService service) {
        this.service = service;
    }

    @GetMapping("/teams/{teamId}/weaknesses")
    public ResponseEntity<TeamTopicReportResponse> getReport(@PathVariable Integer teamId) {
        return ResponseEntity.ok(service.getReport(teamId));
    }
}

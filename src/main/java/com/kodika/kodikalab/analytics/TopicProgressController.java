package com.kodika.kodikalab.analytics;

import com.kodika.kodikalab.analytics.dto.TopicProgressResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/analytics")
@Tag(name = "Analítica", description = "Ranking, progreso y reportes por equipo (US-10, US-11, US-12, US-14).")
public class TopicProgressController {
    private final TopicProgressService service;

    public TopicProgressController(TopicProgressService service) {
        this.service = service;
    }

    @GetMapping("/teams/{teamId}/progress/me/topics")
    @Operation(summary = "Progreso por tema del practicante en un equipo (US-10)",
            description = "Agrupa por tema los problemas asignados al equipo y calcula cuántos resolvió el practicante. "
                    + "Los temas sin intentos salen como SIN_ACTIVIDAD y los de menor cobertura se marcan para reforzar.")
    public ResponseEntity<TopicProgressResponse> getProgress(@PathVariable Integer teamId) {
        return ResponseEntity.ok(service.getProgress(teamId));
    }
}

package com.kodika.kodikalab.analytics;

import com.kodika.kodikalab.analytics.dto.TeamProgressResponse;
import com.kodika.kodikalab.analytics.dto.TeamProgressResponse.Registration;
import com.kodika.kodikalab.competitions.problemresolution.dto.ManualResolutionRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Avance", description = "Registro de resoluciones y avance independiente por equipo (US-09, US-14).")
public class IndependentProgressController {
    private final IndependentProgressService service;

    public IndependentProgressController(IndependentProgressService service) {
        this.service = service;
    }

    @PostMapping("/competitions/teams/{teamId}/problems/{competitionProblemId}/resolutions")
    @ResponseStatus(HttpStatus.CREATED)
    public Registration register(@PathVariable Integer teamId, @PathVariable Integer competitionProblemId,
                                 @RequestBody ManualResolutionRequest request) {
        return service.register(teamId, competitionProblemId, request);
    }

    @GetMapping("/analytics/teams/{teamId}/progress/me")
    public TeamProgressResponse getProgress(@PathVariable Integer teamId) {
        return service.getProgress(teamId);
    }
}

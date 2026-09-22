package com.kodika.kodikalab.controller;

import com.kodika.kodikalab.dto.CreateTeamRequest;
import com.kodika.kodikalab.dto.JoinRequestDto;
import com.kodika.kodikalab.dto.TeamResponse;
import com.kodika.kodikalab.service.TeamService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/teams")
public class TeamsController {

    private final TeamService teamService;

    public TeamsController(TeamService teamService) {
        this.teamService = teamService;
    }

    @PostMapping
    public ResponseEntity<TeamResponse> createTeam(@RequestBody CreateTeamRequest request) {
        return ResponseEntity.ok(teamService.createTeam(request));
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<Void> requestJoin(@PathVariable Long id, @RequestBody JoinRequestDto request) {
        teamService.requestJoin(id, request);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/memberships/{memberId}")
    public ResponseEntity<Void> reviewApplication(@PathVariable Long id, @PathVariable Long memberId) {
        teamService.reviewApplication(id, memberId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<List<TeamResponse>> listMembers(@PathVariable Long id) {
        return ResponseEntity.ok(teamService.listMembers(id));
    }
}


package com.kodika.kodikalab.teams;

import com.kodika.kodikalab.teams.groupmembership.GroupMembershipService;
import com.kodika.kodikalab.teams.joinrequest.GroupJoinRequestService;
import com.kodika.kodikalab.teams.studygroup.CreateStudyGroupRequest;
import com.kodika.kodikalab.teams.studygroup.StudyGroupService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/teams")
public class StudyGroupController {

    private final StudyGroupService studyGroupService;
    private final GroupMembershipService groupMembershipService;
    private final GroupJoinRequestService groupJoinRequestService;

    public StudyGroupController(
            StudyGroupService studyGroupService,
            GroupMembershipService groupMembershipService,
            GroupJoinRequestService groupJoinRequestService) {

        this.studyGroupService = studyGroupService;
        this.groupMembershipService = groupMembershipService;
        this.groupJoinRequestService = groupJoinRequestService;
    }

    // US04 - Crear grupo
    @PostMapping
    public ResponseEntity<Map<String, Object>> createGroup(
            @Valid @RequestBody CreateStudyGroupRequest request) {

        var group = studyGroupService.createGroup(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                Map.of(
                        "message", "Grupo creado correctamente",
                        "groupId", group.getId(),
                        "name", group.getName(),
                        "invitationCode", group.getInvitationCode()
                )
        );
    }

    // US05 - Solicitar ingreso
    @PostMapping("/{id}/join")
    public ResponseEntity<Map<String, Object>> requestJoin(
            @PathVariable("id") Integer groupId) {

        var request = groupJoinRequestService.requestJoin(groupId);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                Map.of(
                        "message", "Solicitud de ingreso registrada correctamente",
                        "requestId", request.getId(),
                        "groupId", request.getGroup().getId(),
                        "status", request.getStatus().name()
                )
        );
    }

    // US06 - Aceptar o rechazar solicitud
    @PatchMapping("/{id}/memberships/{memberId}")
    public ResponseEntity<Map<String, Object>> reviewRequest(
            @PathVariable("id") Integer groupId,
            @PathVariable("memberId") Integer requestId,
            @RequestParam boolean accept) {

        var request = groupJoinRequestService.reviewRequest(
                groupId, requestId, accept
        );

        return ResponseEntity.ok(
                Map.of(
                        "message", "Solicitud revisada correctamente",
                        "requestId", request.getId(),
                        "groupId", request.getGroup().getId(),
                        "status", request.getStatus().name()
                )
        );
    }
}

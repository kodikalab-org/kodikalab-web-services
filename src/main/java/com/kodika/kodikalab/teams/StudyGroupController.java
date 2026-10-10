
package com.kodika.kodikalab.teams;

import com.kodika.kodikalab.teams.groupmembership.GroupMembershipService;
import com.kodika.kodikalab.teams.groupmembership.MembershipStatus;
import com.kodika.kodikalab.teams.groupmembership.dto.JoinGroupResponse;
import com.kodika.kodikalab.teams.groupmembership.dto.ReviewMembershipRequest;
import com.kodika.kodikalab.teams.groupmembership.dto.ReviewMembershipResponse;
import com.kodika.kodikalab.teams.studygroup.CreateStudyGroupRequest;
import com.kodika.kodikalab.teams.studygroup.StudyGroupService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.kodika.kodikalab.teams.studygroup.dto.MyTeamResponse;
import com.kodika.kodikalab.teams.studygroup.dto.StudyGroupResponse;
import com.kodika.kodikalab.teams.studygroup.dto.CreateStudyGroupResponse;
import com.kodika.kodikalab.teams.groupmembership.dto.PendingMembershipResponse;
import com.kodika.kodikalab.common.exception.BadRequestException;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/teams")
@Tag(name = "Equipos", description = "Grupos de entrenamiento, solicitudes de ingreso y membresías (US-04 a US-06).")
public class StudyGroupController {

    private final StudyGroupService studyGroupService;
    private final GroupMembershipService groupMembershipService;

    public StudyGroupController(
            StudyGroupService studyGroupService,
            GroupMembershipService groupMembershipService) {
        this.studyGroupService = studyGroupService;
        this.groupMembershipService = groupMembershipService;
    }
    // US04 y US05 - Listar grupos disponibles
    @GetMapping
    public ResponseEntity<List<StudyGroupResponse>> getAvailableGroups() {

        var groups = studyGroupService.getAvailableGroups();

        return ResponseEntity.ok(
                groups.stream()
                        .map(StudyGroupResponse::from)
                        .toList()
        );
    }

    // US05 / US06 - Mis equipos: los grupos del coach o las membresías (con su estado) del practicante
    @GetMapping("/me")
    public ResponseEntity<List<MyTeamResponse>> getMyTeams() {
        return ResponseEntity.ok(studyGroupService.findMyTeams());
    }

    // US04 - Crear grupo
    @PostMapping
    public ResponseEntity<CreateStudyGroupResponse> createGroup(
            @Valid @RequestBody CreateStudyGroupRequest request) {

        var group = studyGroupService.createGroup(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CreateStudyGroupResponse.from(group));
    }

    // US05 - Solicitar ingreso o ingresar directamente
    @PostMapping("/{id}/join")
    public ResponseEntity<JoinGroupResponse> requestJoin(
            @PathVariable("id") Integer groupId,
            @RequestParam(required = false) String invitationCode) {

        var membership = groupMembershipService.requestJoin(
                groupId, invitationCode
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(JoinGroupResponse.from(membership));
    }

    // US06 - Consultar solicitudes pendientes
    @GetMapping("/{id}/memberships")
    public ResponseEntity<List<PendingMembershipResponse>> getMemberships(
            @PathVariable("id") Integer groupId,
            @RequestParam MembershipStatus status) {

        if (status != MembershipStatus.PENDIENTE) {
            throw new BadRequestException(
                    "Solo se permite consultar solicitudes PENDIENTE"
            );
        }

        var memberships = groupMembershipService.getPendingRequests(groupId);

        return ResponseEntity.ok(
                memberships.stream()
                        .map(PendingMembershipResponse::from)
                        .toList()
        );
    }

    // US06 - Aceptar o rechazar solicitud
    @PatchMapping("/{id}/memberships/{memberId}")
    public ResponseEntity<ReviewMembershipResponse> reviewRequest(
            @PathVariable("id") Integer groupId,
            @PathVariable("memberId") Integer membershipId,
            @Valid @RequestBody ReviewMembershipRequest request) {

        boolean accept = request.decision()
                == ReviewMembershipRequest.Decision.ACEPTAR;

        var membership = groupMembershipService.reviewRequest(
                groupId, membershipId, accept
        );

        return ResponseEntity.ok(
                ReviewMembershipResponse.from(membership)
        );
    }
}

package com.kodika.kodikalab.teams;

import com.kodika.kodikalab.common.exception.BadRequestException;
import com.kodika.kodikalab.teams.groupmembership.GroupMembershipService;
import com.kodika.kodikalab.teams.groupmembership.MembershipStatus;
import com.kodika.kodikalab.teams.groupmembership.dto.ClarificationResponse;
import com.kodika.kodikalab.teams.groupmembership.dto.ClarificationResponseRequest;
import com.kodika.kodikalab.teams.groupmembership.dto.JoinGroupResponse;
import com.kodika.kodikalab.teams.groupmembership.dto.PendingMembershipResponse;
import com.kodika.kodikalab.teams.groupmembership.dto.ReviewMembershipRequest;
import com.kodika.kodikalab.teams.groupmembership.dto.ReviewMembershipResponse;
import com.kodika.kodikalab.teams.studygroup.CreateStudyGroupRequest;
import com.kodika.kodikalab.teams.studygroup.StudyGroupService;
import com.kodika.kodikalab.teams.studygroup.UpdateStudyGroupDraftRequest;
import com.kodika.kodikalab.teams.studygroup.dto.CreateStudyGroupResponse;
import com.kodika.kodikalab.teams.studygroup.dto.MyTeamResponse;
import com.kodika.kodikalab.teams.studygroup.dto.StudyGroupDetailResponse;
import com.kodika.kodikalab.teams.studygroup.dto.StudyGroupResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@RestController
@RequestMapping("/teams")
@Tag(name = "Equipos", description = "Grupos de entrenamiento, borradores, solicitudes de ingreso y membresías (US-04 a US-06).")
public class StudyGroupController {

    private final StudyGroupService studyGroupService;
    private final GroupMembershipService groupMembershipService;

    public StudyGroupController(
            StudyGroupService studyGroupService,
            GroupMembershipService groupMembershipService) {
        this.studyGroupService = studyGroupService;
        this.groupMembershipService = groupMembershipService;
    }

    // US04 y US05 - Listar grupos publicados y activos
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

    // US04 - Crear grupo publicado o guardar un borrador con "draft": true
    @PostMapping
    public ResponseEntity<CreateStudyGroupResponse> createGroup(
            @Valid @RequestBody CreateStudyGroupRequest request) {

        var group = studyGroupService.createGroup(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CreateStudyGroupResponse.from(group));
    }

    // US04 - Guardar cambios de un borrador
    @PatchMapping("/{id}/draft")
    public ResponseEntity<StudyGroupDetailResponse> updateDraft(
            @PathVariable("id") Integer groupId,
            @Valid @RequestBody UpdateStudyGroupDraftRequest request) {

        return ResponseEntity.ok(
                StudyGroupDetailResponse.from(
                        studyGroupService.updateDraft(groupId, request)
                )
        );
    }

    // US04 - Publicar un borrador
    @PostMapping("/{id}/publish")
    public ResponseEntity<StudyGroupDetailResponse> publishGroup(
            @PathVariable("id") Integer groupId) {

        return ResponseEntity.ok(
                StudyGroupDetailResponse.from(
                        studyGroupService.publishGroup(groupId)
                )
        );
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

    // US06 - Consultar solicitudes pendientes o con aclaración solicitada
    @GetMapping("/{id}/memberships")
    public ResponseEntity<List<PendingMembershipResponse>> getMemberships(
            @PathVariable("id") Integer groupId,
            @RequestParam MembershipStatus status) {

        if (status != MembershipStatus.PENDIENTE
                && status != MembershipStatus.ACLARACION_SOLICITADA) {
            throw new BadRequestException(
                    "Solo se permite consultar solicitudes PENDIENTE o ACLARACION_SOLICITADA"
            );
        }

        var memberships = groupMembershipService.getRequests(groupId, status);

        return ResponseEntity.ok(
                memberships.stream()
                        .map(PendingMembershipResponse::from)
                        .toList()
        );
    }

    // US06 - Aceptar, rechazar o pedir aclaración
    @PatchMapping("/{id}/memberships/{memberId}")
    public ResponseEntity<ReviewMembershipResponse> reviewRequest(
            @PathVariable("id") Integer groupId,
            @PathVariable("memberId") Integer membershipId,
            @Valid @RequestBody ReviewMembershipRequest request) {

        var membership = groupMembershipService.reviewRequest(
                groupId, membershipId, request.decision(), request.message()
        );

        return ResponseEntity.ok(
                ReviewMembershipResponse.from(membership)
        );
    }

    // US06 - El postulante responde la aclaración solicitada
    @PostMapping("/{id}/memberships/{memberId}/clarification")
    public ResponseEntity<ClarificationResponse> respondClarification(
            @PathVariable("id") Integer groupId,
            @PathVariable("memberId") Integer membershipId,
            @Valid @RequestBody ClarificationResponseRequest request) {

        var membership = groupMembershipService.respondClarification(
                groupId, membershipId, request.message()
        );

        return ResponseEntity.ok(
                ClarificationResponse.from(membership)
        );
    }
}

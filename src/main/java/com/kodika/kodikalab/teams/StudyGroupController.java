package com.kodika.kodikalab.teams;

import com.kodika.kodikalab.teams.groupmembership.GroupMembershipService;
import com.kodika.kodikalab.teams.studygroup.StudyGroupService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Plantilla del controller de teams en {@code /api/teams}. Aún no expone endpoints:
 * agregar cada {@code @GetMapping}/{@code @PostMapping}... con su historia y su contrato en
 * {@code docs/sdd/03-api-contracts.md}, delegando en el servicio de la entidad.
 */
@RestController
@RequestMapping("/teams")
public class StudyGroupController {
    private final StudyGroupService studyGroupService;
    private final GroupMembershipService groupMembershipService;

    public StudyGroupController(StudyGroupService studyGroupService,
                                GroupMembershipService groupMembershipService) {
        this.studyGroupService = studyGroupService;
        this.groupMembershipService = groupMembershipService;
    }
}

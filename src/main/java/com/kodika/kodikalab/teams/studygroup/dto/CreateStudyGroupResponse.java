package com.kodika.kodikalab.teams.studygroup.dto;

import com.kodika.kodikalab.teams.studygroup.GroupStatus;
import com.kodika.kodikalab.teams.studygroup.StudyGroup;

public record CreateStudyGroupResponse(
        String message,
        Integer groupId,
        String name,
        GroupStatus status,
        String invitationCode
) {
    public static CreateStudyGroupResponse from(StudyGroup group) {
        boolean draft = group.getStatus() == GroupStatus.BORRADOR;

        return new CreateStudyGroupResponse(
                draft
                        ? "Borrador del grupo guardado correctamente"
                        : "Grupo creado correctamente",
                group.getId(),
                group.getName(),
                group.getStatus(),
                group.getInvitationCode()
        );
    }
}

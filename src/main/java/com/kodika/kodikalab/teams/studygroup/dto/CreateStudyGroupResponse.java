package com.kodika.kodikalab.teams.studygroup.dto;

import com.kodika.kodikalab.teams.studygroup.StudyGroup;

public record CreateStudyGroupResponse(
        String message,
        Integer groupId,
        String name,
        String invitationCode
) {
    public static CreateStudyGroupResponse from(StudyGroup group) {
        return new CreateStudyGroupResponse(
                "Grupo creado correctamente",
                group.getId(),
                group.getName(),
                group.getInvitationCode()
        );
    }
}
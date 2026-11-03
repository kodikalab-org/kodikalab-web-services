package com.kodika.kodikalab.teams.studygroup.dto;

import com.kodika.kodikalab.teams.studygroup.GroupStatus;
import com.kodika.kodikalab.teams.studygroup.GroupVisibility;
import com.kodika.kodikalab.teams.studygroup.StudyGroup;

/** Datos completos de un grupo, incluido su estado, para crear, editar un borrador y publicarlo. */
public record StudyGroupDetailResponse(
        Integer groupId,
        String name,
        String description,
        String expectedLevel,
        Integer maxCapacity,
        String sessionSchedule,
        GroupStatus status,
        GroupVisibility visibility,
        String invitationCode
) {
    public static StudyGroupDetailResponse from(StudyGroup group) {
        return new StudyGroupDetailResponse(
                group.getId(),
                group.getName(),
                group.getDescription(),
                group.getExpectedLevel(),
                group.getMaxCapacity(),
                group.getSessionSchedule(),
                group.getStatus(),
                group.getVisibility(),
                group.getInvitationCode()
        );
    }
}

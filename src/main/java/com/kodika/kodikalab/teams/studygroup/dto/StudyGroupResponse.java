package com.kodika.kodikalab.teams.studygroup.dto;

import com.kodika.kodikalab.teams.studygroup.GroupStatus;
import com.kodika.kodikalab.teams.studygroup.GroupVisibility;
import com.kodika.kodikalab.teams.studygroup.StudyGroup;

public record StudyGroupResponse(
        Integer groupId,
        String name,
        String description,
        String expectedLevel,
        Integer maxCapacity,
        String sessionSchedule,
        GroupStatus status,
        GroupVisibility visibility
) {
    public static StudyGroupResponse from(StudyGroup group) {
        return new StudyGroupResponse(
                group.getId(),
                group.getName(),
                group.getDescription(),
                group.getExpectedLevel(),
                group.getMaxCapacity(),
                group.getSessionSchedule(),
                group.getStatus(),
                group.getVisibility()
        );
    }
}
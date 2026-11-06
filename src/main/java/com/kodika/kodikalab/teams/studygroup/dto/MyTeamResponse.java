package com.kodika.kodikalab.teams.studygroup.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kodika.kodikalab.teams.groupmembership.MembershipStatus;
import com.kodika.kodikalab.teams.groupmembership.TeamRole;
import com.kodika.kodikalab.teams.studygroup.GroupStatus;
import com.kodika.kodikalab.teams.studygroup.GroupVisibility;
import java.time.OffsetDateTime;

public record MyTeamResponse(Integer groupId, String name, String description, String expectedLevel,
                             Integer maxCapacity, String sessionSchedule, GroupStatus status,
                             GroupVisibility visibility,
                             @JsonInclude(JsonInclude.Include.NON_NULL) String invitationCode,
                             @JsonInclude(JsonInclude.Include.NON_NULL) Long activeMembers,
                             @JsonInclude(JsonInclude.Include.NON_NULL) Membership membership) {
    public record Membership(Integer membershipId, MembershipStatus status, TeamRole teamRole,
                             OffsetDateTime joinedAt, OffsetDateTime leftAt) {
    }
}

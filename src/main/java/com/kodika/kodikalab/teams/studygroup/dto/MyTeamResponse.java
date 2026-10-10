package com.kodika.kodikalab.teams.studygroup.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kodika.kodikalab.teams.groupmembership.GroupMembership;
import com.kodika.kodikalab.teams.groupmembership.MembershipStatus;
import com.kodika.kodikalab.teams.groupmembership.TeamRole;
import com.kodika.kodikalab.teams.studygroup.GroupStatus;
import com.kodika.kodikalab.teams.studygroup.GroupVisibility;
import com.kodika.kodikalab.teams.studygroup.StudyGroup;
import java.time.OffsetDateTime;

/**
 * Un equipo del usuario autenticado. Para un coach es un grupo que creó, con su código de invitación y la cantidad de
 * integrantes y de solicitudes pendientes. Para un practicante es un grupo donde tiene o tuvo una membresía, con el
 * estado de esa membresía (así sabe si lo aceptaron). Los campos que no corresponden al rol no se incluyen.
 */
public record MyTeamResponse(
        Integer groupId,
        String name,
        String description,
        String expectedLevel,
        Integer maxCapacity,
        String sessionSchedule,
        GroupStatus status,
        GroupVisibility visibility,
        @JsonInclude(JsonInclude.Include.NON_NULL) String invitationCode,
        @JsonInclude(JsonInclude.Include.NON_NULL) Long activeMembers,
        @JsonInclude(JsonInclude.Include.NON_NULL) Long pendingRequests,
        @JsonInclude(JsonInclude.Include.NON_NULL) Membership membership
) {
    public record Membership(
            Integer membershipId,
            MembershipStatus status,
            TeamRole teamRole,
            OffsetDateTime joinedAt,
            OffsetDateTime leftAt
    ) {
    }

    public static MyTeamResponse ofCoach(StudyGroup group, long activeMembers, long pendingRequests) {
        return new MyTeamResponse(group.getId(), group.getName(), group.getDescription(), group.getExpectedLevel(),
                group.getMaxCapacity(), group.getSessionSchedule(), group.getStatus(), group.getVisibility(),
                group.getInvitationCode(), activeMembers, pendingRequests, null);
    }

    public static MyTeamResponse ofMember(GroupMembership membership) {
        StudyGroup group = membership.getGroup();
        return new MyTeamResponse(group.getId(), group.getName(), group.getDescription(), group.getExpectedLevel(),
                group.getMaxCapacity(), group.getSessionSchedule(), group.getStatus(), group.getVisibility(),
                null, null, null,
                new Membership(membership.getId(), membership.getStatus(), membership.getTeamRole(),
                        membership.getJoinedAt(), membership.getLeftAt()));
    }
}

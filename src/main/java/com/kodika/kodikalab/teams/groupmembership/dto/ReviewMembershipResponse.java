package com.kodika.kodikalab.teams.groupmembership.dto;

import com.kodika.kodikalab.teams.groupmembership.GroupMembership;
import com.kodika.kodikalab.teams.groupmembership.MembershipStatus;

public record ReviewMembershipResponse(
        String message,
        Integer membershipId,
        Integer groupId,
        MembershipStatus status
) {
    public static ReviewMembershipResponse from(GroupMembership membership) {
        return new ReviewMembershipResponse(
                "Solicitud revisada correctamente",
                membership.getId(),
                membership.getGroup().getId(),
                membership.getStatus()
        );
    }
}

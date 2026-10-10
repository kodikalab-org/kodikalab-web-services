package com.kodika.kodikalab.teams.groupmembership.dto;

import com.kodika.kodikalab.teams.groupmembership.GroupMembership;
import com.kodika.kodikalab.teams.groupmembership.MembershipStatus;
import java.time.OffsetDateTime;

public record PendingMembershipResponse(
        Integer membershipId,
        Integer groupId,
        Integer practitionerId,
        MembershipStatus status,
        OffsetDateTime requestedAt
) {
    public static PendingMembershipResponse from(GroupMembership membership) {
        return new PendingMembershipResponse(
                membership.getId(),
                membership.getGroup().getId(),
                membership.getPractitioner().getUserId(),
                membership.getStatus(),
                membership.getJoinedAt()
        );
    }
}

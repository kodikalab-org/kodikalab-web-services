package com.kodika.kodikalab.teams.groupmembership.dto;

import com.kodika.kodikalab.teams.groupmembership.GroupMembership;
import com.kodika.kodikalab.teams.groupmembership.MembershipStatus;
import java.time.OffsetDateTime;

/** Resultado de responder una aclaración: la solicitud vuelve a quedar pendiente de revisión. */
public record ClarificationResponse(
        String message,
        Integer membershipId,
        Integer groupId,
        MembershipStatus status,
        String clarificationRequest,
        String clarificationResponse,
        OffsetDateTime respondedAt
) {
    public static ClarificationResponse from(GroupMembership membership) {
        return new ClarificationResponse(
                "Aclaración enviada; la solicitud vuelve a revisión del coach",
                membership.getId(),
                membership.getGroup().getId(),
                membership.getStatus(),
                membership.getClarificationRequest(),
                membership.getClarificationResponse(),
                membership.getClarificationRespondedAt()
        );
    }
}

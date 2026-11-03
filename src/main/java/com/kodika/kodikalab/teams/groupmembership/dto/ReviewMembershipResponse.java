package com.kodika.kodikalab.teams.groupmembership.dto;

import com.kodika.kodikalab.teams.groupmembership.GroupMembership;
import com.kodika.kodikalab.teams.groupmembership.MembershipStatus;

public record ReviewMembershipResponse(
        String message,
        Integer membershipId,
        Integer groupId,
        MembershipStatus status,
        String clarificationRequest
) {
    public static ReviewMembershipResponse from(GroupMembership membership) {
        boolean clarification = membership.getStatus() == MembershipStatus.ACLARACION_SOLICITADA;

        return new ReviewMembershipResponse(
                clarification
                        ? "Aclaración solicitada al postulante"
                        : "Solicitud revisada correctamente",
                membership.getId(),
                membership.getGroup().getId(),
                membership.getStatus(),
                clarification ? membership.getClarificationRequest() : null
        );
    }
}

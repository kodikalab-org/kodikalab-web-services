package com.kodika.kodikalab.teams.groupmembership.dto;

import com.kodika.kodikalab.teams.groupmembership.GroupMembership;
import com.kodika.kodikalab.teams.groupmembership.MembershipStatus;

public record JoinGroupResponse(
        String message,
        Integer membershipId,
        Integer groupId,
        MembershipStatus status
) {
    public static JoinGroupResponse from(GroupMembership membership) {
        boolean active = membership.getStatus() == MembershipStatus.ACTIVO;

        return new JoinGroupResponse(
                active
                        ? "Ingreso al grupo registrado correctamente"
                        : "Solicitud de ingreso registrada correctamente",
                membership.getId(),
                membership.getGroup().getId(),
                membership.getStatus()
        );
    }
}

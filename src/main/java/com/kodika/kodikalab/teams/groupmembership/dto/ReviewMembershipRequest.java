package com.kodika.kodikalab.teams.groupmembership.dto;

import jakarta.validation.constraints.NotNull;

public record ReviewMembershipRequest(
        @NotNull Decision decision
) {
    public enum Decision {
        ACEPTAR,
        RECHAZAR
    }
}
package com.kodika.kodikalab.teams.groupmembership.dto;

import jakarta.validation.constraints.NotBlank;

/** Ingreso a un grupo usando solo su código de invitación (US05). El servicio la recorta y la pasa a mayúsculas. */
public record JoinByCodeRequest(
        @NotBlank(message = "El código de invitación es obligatorio")
        String invitationCode
) {
}

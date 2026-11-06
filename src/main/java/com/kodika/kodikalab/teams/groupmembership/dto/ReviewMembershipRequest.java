package com.kodika.kodikalab.teams.groupmembership.dto;

import com.kodika.kodikalab.teams.groupmembership.MembershipDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Decisión del coach sobre una solicitud (US06).
 *
 * <p>{@code message} es obligatorio cuando la decisión es {@code SOLICITAR_ACLARACION} y se ignora en los demás
 * casos. La validación condicional se hace en el servicio para mantener un único contrato de error.
 */
public record ReviewMembershipRequest(
        @NotNull(message = "La decisión es obligatoria") MembershipDecision decision,

        @Size(max = 500, message = "El mensaje no puede superar 500 caracteres")
        String message
) {
}

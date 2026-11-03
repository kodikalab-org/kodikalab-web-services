package com.kodika.kodikalab.teams.groupmembership.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Respuesta del postulante a una aclaración solicitada por el coach (US06). */
public record ClarificationResponseRequest(
        @NotBlank(message = "La respuesta es obligatoria")
        @Size(max = 500, message = "La respuesta no puede superar 500 caracteres")
        String message
) {
}

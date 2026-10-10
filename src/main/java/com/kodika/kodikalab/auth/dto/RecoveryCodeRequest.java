package com.kodika.kodikalab.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Consulta del código de recuperación vigente: exige la contraseña actual además del token. */
public record RecoveryCodeRequest(@NotBlank(message = "La contraseña es obligatoria") String password) {

    @Override
    public String toString() {
        return "RecoveryCodeRequest[password=REDACTED]";
    }
}

package com.kodika.kodikalab.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;

/** Paso 1: el titular pide que se le envíe un código por correo. */
public record PasswordResetRequest(
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo debe tener un formato válido")
        @Size(max = 100, message = "El correo debe tener como máximo 100 caracteres") String email) {

    public PasswordResetRequest {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}

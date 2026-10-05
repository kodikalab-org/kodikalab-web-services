package com.kodika.kodikalab.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record LoginRequest(
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo debe tener un formato válido")
        @Size(max = 100, message = "El correo debe tener como máximo 100 caracteres") String email,
        @NotBlank(message = "La contraseña es obligatoria") String password) {

    public LoginRequest {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
        // Do not trim passwords: spaces can be part of the registered credential.
    }

    @Override
    public String toString() {
        return "LoginRequest[password=REDACTED]";
    }
}

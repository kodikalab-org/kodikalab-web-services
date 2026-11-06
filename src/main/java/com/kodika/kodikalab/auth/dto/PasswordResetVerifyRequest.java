package com.kodika.kodikalab.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

/** Paso 2: el titular comprueba el código que recibió por correo. */
public record PasswordResetVerifyRequest(
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo debe tener un formato válido")
        @Size(max = 100, message = "El correo debe tener como máximo 100 caracteres") String email,
        @NotBlank(message = "El código es obligatorio")
        @Pattern(regexp = "\\d{6}", message = "El código debe tener 6 dígitos") String code) {

    public PasswordResetVerifyRequest {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
        code = code == null ? null : code.replaceAll("\\s+", "");
    }

    @Override
    public String toString() {
        return "PasswordResetVerifyRequest[code=REDACTED]";
    }
}

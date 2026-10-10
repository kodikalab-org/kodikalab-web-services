package com.kodika.kodikalab.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

/**
 * Recuperación de acceso sin correo: el titular demuestra que es dueño de la cuenta con su código de recuperación
 * y define una contraseña nueva, que cumple la misma política que el registro.
 */
public record RecoveryRequest(
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo debe tener un formato válido")
        @Size(max = 100, message = "El correo debe tener como máximo 100 caracteres") String email,
        @NotBlank(message = "El código de recuperación es obligatorio")
        @Size(max = 64, message = "El código de recuperación no es válido") String recoveryCode,
        @NotBlank(message = "La contraseña nueva es obligatoria")
        @Pattern(regexp = "(?s)(?=.*\\p{Lu})(?=.*\\p{Nd}).{8,}",
                message = "La contraseña debe contener al menos 8 caracteres, una mayúscula y un número")
        String newPassword) {

    public RecoveryRequest {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
        recoveryCode = recoveryCode == null ? null : recoveryCode.strip();
        // La contraseña nueva no se recorta: los espacios pueden formar parte de la credencial.
    }

    @Override
    public String toString() {
        return "RecoveryRequest[recoveryCode=REDACTED, newPassword=REDACTED]";
    }
}

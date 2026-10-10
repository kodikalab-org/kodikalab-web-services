package com.kodika.kodikalab.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kodika.kodikalab.users.Role;

/**
 * Respuesta de registro y de login. El registro solo informa {@code message}, {@code email} y {@code role}; el login
 * agrega el token de acceso. Los campos ausentes no se serializan.
 *
 * @param token        JWT firmado (solo en el login)
 * @param tokenType    siempre {@code Bearer} (solo en el login)
 * @param expiresIn    vigencia del token en segundos (solo en el login)
 * @param recoveryCode código de recuperación de la cuenta, que el titular debe guardar (solo en el registro)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(String message, String email, Role role, String token, String tokenType, Long expiresIn,
                           String recoveryCode) {

    public AuthResponse(String message, String email, Role role) {
        this(message, email, role, null, null, null, null);
    }

    public AuthResponse(String message, String email, Role role, String token, String tokenType, Long expiresIn) {
        this(message, email, role, token, tokenType, expiresIn, null);
    }

    @Override
    public String toString() {
        return "AuthResponse[message=" + message + ", email=" + email + ", role=" + role
                + ", token=REDACTED, recoveryCode=REDACTED]";
    }
}

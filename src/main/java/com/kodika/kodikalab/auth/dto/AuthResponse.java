package com.kodika.kodikalab.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kodika.kodikalab.users.Role;

/**
 * Respuesta de registro y de login. El registro solo informa {@code message}, {@code email} y {@code role}; el login
 * agrega el token de acceso. Los campos ausentes no se serializan.
 *
 * @param token     JWT firmado (solo en el login)
 * @param tokenType siempre {@code Bearer} (solo en el login)
 * @param expiresIn vigencia del token en segundos (solo en el login)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(String message, String email, Role role, String token, String tokenType, Long expiresIn) {

    public AuthResponse(String message, String email, Role role) {
        this(message, email, role, null, null, null);
    }

    @Override
    public String toString() {
        return "AuthResponse[message=" + message + ", email=" + email + ", role=" + role + ", token=REDACTED]";
    }
}

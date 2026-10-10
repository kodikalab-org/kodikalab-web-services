package com.kodika.kodikalab.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Parámetros del token de acceso (prefijo {@code jwt}). El secreto no tiene valor por defecto y nunca se imprime.
 *
 * @param secret     clave HMAC en texto (mínimo 32 caracteres); variable de entorno {@code JWT_SECRET}
 * @param expiration vigencia del token en milisegundos; variable de entorno {@code JWT_EXPIRATION}
 * @param issuer     emisor que se firma y se exige al validar
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("86400000") long expiration,
        @DefaultValue("kodikalab") String issuer) {

    @Override
    public String toString() {
        return "JwtProperties[secret=REDACTED, expiration=" + expiration + ", issuer=" + issuer + "]";
    }
}

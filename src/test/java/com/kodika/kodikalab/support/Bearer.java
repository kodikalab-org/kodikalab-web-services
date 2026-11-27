package com.kodika.kodikalab.support;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Utilidades de los tests de integración para autenticarse con el token Bearer de {@code POST /auth/login}. */
public final class Bearer {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Bearer() {
    }

    /** Adjunta {@code Authorization: Bearer <token>}; con token nulo no agrega nada (petición anónima). */
    public static RequestPostProcessor of(String token) {
        return request -> {
            if (token != null) {
                request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            }
            return request;
        };
    }

    /** Extrae el campo {@code token} del cuerpo de una respuesta de login. */
    public static String tokenFrom(String loginResponseBody) {
        try {
            var token = MAPPER.readTree(loginResponseBody).get("token");
            if (token == null || !token.isString()) {
                throw new IllegalStateException("La respuesta de login no contiene token: " + loginResponseBody);
            }
            return token.stringValue();
        } catch (JacksonException exception) {
            throw new IllegalStateException("Respuesta de login ilegible", exception);
        }
    }
}

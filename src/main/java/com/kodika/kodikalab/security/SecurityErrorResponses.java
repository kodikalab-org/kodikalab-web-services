package com.kodika.kodikalab.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/** Cuerpo JSON {@code {message, errors}} de los errores de seguridad, igual al de los demás módulos. */
final class SecurityErrorResponses {
    private SecurityErrorResponses() {
    }

    static void write(ObjectMapper mapper, HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        mapper.writeValue(response.getWriter(), new ErrorBody(message, Map.of()));
    }

    record ErrorBody(String message, Map<String, String> errors) {
    }
}

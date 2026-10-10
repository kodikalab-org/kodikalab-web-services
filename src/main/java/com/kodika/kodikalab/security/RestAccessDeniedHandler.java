package com.kodika.kodikalab.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/** 403 en JSON: la cuenta está autenticada pero su rol (o su estado) no permite la operación. */
public class RestAccessDeniedHandler implements AccessDeniedHandler {
    static final String FORBIDDEN = "No tiene permisos para realizar esta acción";

    private final ObjectMapper mapper;

    public RestAccessDeniedHandler(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        String message = accessDeniedException instanceof AccountDisabledException
                ? accessDeniedException.getMessage() : FORBIDDEN;
        SecurityErrorResponses.write(mapper, response, HttpStatus.FORBIDDEN, message);
    }
}

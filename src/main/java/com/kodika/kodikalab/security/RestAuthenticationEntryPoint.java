package com.kodika.kodikalab.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/** 401 en JSON cuando falta el token o no es válido (RFC 6750: encabezado {@code WWW-Authenticate: Bearer}). */
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
    static final String MISSING_TOKEN = "Debe iniciar sesión: envíe el token en el encabezado Authorization (Bearer)";
    static final String INVALID_TOKEN = "El token es inválido o expiró: inicie sesión nuevamente";

    private final ObjectMapper mapper;

    public RestAuthenticationEntryPoint(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        boolean invalid = Boolean.TRUE.equals(request.getAttribute(JwtAuthenticationFilter.INVALID_TOKEN_ATTRIBUTE));
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, invalid ? "Bearer error=\"invalid_token\"" : "Bearer");
        SecurityErrorResponses.write(mapper, response, HttpStatus.UNAUTHORIZED, invalid ? INVALID_TOKEN : MISSING_TOKEN);
    }
}

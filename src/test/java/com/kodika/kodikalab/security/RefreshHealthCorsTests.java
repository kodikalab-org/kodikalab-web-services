package com.kodika.kodikalab.security;

import com.kodika.kodikalab.auth.AccountRecoveryService;
import com.kodika.kodikalab.auth.AuthController;
import com.kodika.kodikalab.auth.AuthServiceImpl;
import com.kodika.kodikalab.auth.RecoveryCodeService;
import com.kodika.kodikalab.common.web.HealthController;
import com.kodika.kodikalab.config.CorsConfig;
import com.kodika.kodikalab.config.SecurityConfig;
import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.support.TestJwt;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserService;
import com.kodika.kodikalab.users.UserStatus;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cadena de seguridad real (filtro JWT, reglas, CORS) sin base de datos: {@link UserService} es un mock.
 * Cubre {@code POST /auth/refresh}, {@code GET /health} y la cabecera CORS expuesta {@code WWW-Authenticate}.
 */
@WebMvcTest(controllers = {AuthController.class, HealthController.class})
@Import({SecurityConfig.class, CorsConfig.class, AuthServiceImpl.class, CurrentUserResolver.class,
        RecoveryCodeService.class, JwtService.class})
@TestPropertySource(properties = {
        "jwt.secret=" + TestJwt.SECRET,
        "jwt.expiration=" + TestJwt.EXPIRATION_MILLIS,
        "kodikalab.cors.allowed-origins=http://localhost:3000"})
class RefreshHealthCorsTests {
    private static final String ORIGIN = "http://localhost:3000";

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockitoBean UserService users;
    @MockitoBean AccountRecoveryService recovery;

    User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(7);
        user.setEmail("test@gmail.com");
        user.setPasswordHash("$2a$10$hash");
        user.setRole(Role.PRACTICANTE);
        user.setStatus(UserStatus.ACTIVO);
        when(users.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));
    }

    @Test
    void refreshWithValidTokenReturnsNewUsableToken() throws Exception {
        String original = jwt.issue(user).token();
        String body = mvc.perform(post("/auth/refresh").header(HttpHeaders.AUTHORIZATION, "Bearer " + original))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Sesión renovada"))
                .andExpect(jsonPath("$.email").value("test@gmail.com"))
                .andExpect(jsonPath("$.role").value("PRACTICANTE"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(TestJwt.EXPIRATION_MILLIS / 1000))
                .andExpect(jsonPath("$.token").value(not(blankOrNullString())))
                .andExpect(jsonPath("$.recoveryCode").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String renewed = com.jayway.jsonpath.JsonPath.read(body, "$.token");
        org.assertj.core.api.Assertions.assertThat(renewed).isNotEqualTo(original);
        // El token nuevo sirve para autenticarse de nuevo.
        mvc.perform(post("/auth/refresh").header(HttpHeaders.AUTHORIZATION, "Bearer " + renewed))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("Sesión renovada"));
    }

    @Test
    void refreshWithoutTokenIs401() throws Exception {
        mvc.perform(post("/auth/refresh")).andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.message")
                        .value("Debe iniciar sesión: envíe el token en el encabezado Authorization (Bearer)"))
                .andExpect(jsonPath("$.errors").isMap());
    }

    @Test
    void refreshWithInvalidTokenIs401() throws Exception {
        mvc.perform(post("/auth/refresh").header(HttpHeaders.AUTHORIZATION, "Bearer no.es.un.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("El token es inválido o expiró: inicie sesión nuevamente"));
    }

    @Test
    void refreshForASuspendedAccountIs403() throws Exception {
        String token = jwt.issue(user).token();
        user.setStatus(UserStatus.SUSPENDIDO);
        mvc.perform(post("/auth/refresh").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("La cuenta no está habilitada"));
    }

    @Test
    void healthIsPublicAndDoesNotTouchTheDatabase() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        org.mockito.Mockito.verifyNoInteractions(users);
    }

    @Test
    void corsPreflightExposesWwwAuthenticate() throws Exception {
        mvc.perform(options("/auth/refresh").header(HttpHeaders.ORIGIN, ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN));
    }

    @Test
    void corsResponseToAllowedOriginExposesWwwAuthenticate() throws Exception {
        mvc.perform(post("/auth/refresh").header(HttpHeaders.ORIGIN, ORIGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        containsStringIgnoringCase(HttpHeaders.WWW_AUTHENTICATE)));
        mvc.perform(get("/health").header(HttpHeaders.ORIGIN, ORIGIN)).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        containsStringIgnoringCase(HttpHeaders.WWW_AUTHENTICATE)));
    }
}

package com.kodika.kodikalab.security;

import com.kodika.kodikalab.support.Bearer;
import com.kodika.kodikalab.support.TestJwt;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserRepository;
import com.kodika.kodikalab.users.UserStatus;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cadena completa de Spring Security (filtro JWT, reglas por rol, errores 401/403, CORS y documentación) con
 * PostgreSQL en un schema aislado. Nunca usa la base de desarrollo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "SECURITY_TEST_DB_URL", matches = ".+")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SecurityIntegrationTests {
    private static final String SCHEMA = "security_test_" + UUID.randomUUID().toString().replace("-", "");
    private static final String PASSWORD = "Password123";
    private static final String FORBIDDEN = "No tiene permisos para realizar esta acción";
    private static final String MISSING_TOKEN = "Debe iniciar sesión: envíe el token en el encabezado Authorization (Bearer)";
    private static final String INVALID_TOKEN = "El token es inválido o expiró: inicie sesión nuevamente";
    private static boolean schemaCreated;

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;

    record Endpoint(HttpMethod method, String path, String body) {
        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    static final List<Endpoint> COACH_ONLY = List.of(
            new Endpoint(HttpMethod.POST, "/teams", "{}"),
            new Endpoint(HttpMethod.GET, "/teams/1/memberships?status=PENDIENTE", null),
            new Endpoint(HttpMethod.PATCH, "/teams/1/memberships/1", "{\"decision\":\"ACEPTAR\"}"),
            new Endpoint(HttpMethod.POST, "/problems", "{}"),
            new Endpoint(HttpMethod.POST, "/problems/assign", "{}"),
            new Endpoint(HttpMethod.POST, "/competitions", "{}"),
            new Endpoint(HttpMethod.POST, "/competitions/1/official-result", "{}"),
            new Endpoint(HttpMethod.PUT, "/competitions/1/official-result", "{}"),
            new Endpoint(HttpMethod.GET, "/competitions/1/official-result", null),
            new Endpoint(HttpMethod.GET, "/competitions/teams/1/official-results", null),
            new Endpoint(HttpMethod.GET, "/analytics/teams/1/weaknesses", null));

    static final List<Endpoint> PRACTITIONER_ONLY = List.of(
            new Endpoint(HttpMethod.POST, "/teams/1/join", null),
            new Endpoint(HttpMethod.POST, "/competitions/teams/1/problems/1/resolutions", "{\"language\":\"Java 21\"}"),
            new Endpoint(HttpMethod.GET, "/analytics/teams/1/progress/me", null));

    static final List<Endpoint> ANY_ACCOUNT = List.of(
            new Endpoint(HttpMethod.GET, "/users/me", null),
            new Endpoint(HttpMethod.PUT, "/users/me", "{}"),
            new Endpoint(HttpMethod.GET, "/teams", null),
            new Endpoint(HttpMethod.GET, "/problems", null),
            new Endpoint(HttpMethod.GET, "/problems/assigned", null),
            new Endpoint(HttpMethod.GET, "/problems/assigned/1", null),
            new Endpoint(HttpMethod.GET, "/analytics/teams/1/standings", null));

    static Stream<Endpoint> allProtected() {
        return Stream.of(COACH_ONLY, PRACTITIONER_ONLY, ANY_ACCOUNT).flatMap(List::stream);
    }

    static Stream<Endpoint> coachOnly() {
        return COACH_ONLY.stream();
    }

    static Stream<Endpoint> practitionerOnly() {
        return PRACTITIONER_ONLY.stream();
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(System.getenv("SECURITY_TEST_DB_URL"),
                System.getenv().getOrDefault("SECURITY_TEST_DB_USER", "postgres"),
                System.getenv().getOrDefault("SECURITY_TEST_DB_PASSWORD", ""));
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) throws SQLException {
        try (Connection connection = connect(); var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + SCHEMA);
            schemaCreated = true;
        }
        registry.add("spring.datasource.url", () -> System.getenv("SECURITY_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("SECURITY_TEST_DB_USER", "postgres"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("SECURITY_TEST_DB_PASSWORD", ""));
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA);
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("jwt.secret", () -> TestJwt.SECRET);
        registry.add("kodikalab.cors.allowed-origins", () -> "http://localhost:3000");
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        if (schemaCreated) {
            try (Connection connection = connect(); var statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
            }
        }
    }

    private User account(Role role, UserStatus status) {
        User user = new User();
        user.setFullName("Usuario Prueba");
        user.setEmail("test.security." + UUID.randomUUID() + "@gmail.com");
        user.setPasswordHash(encoder.encode(PASSWORD));
        user.setRole(role);
        user.setStatus(status);
        user.setCreatedAt(OffsetDateTime.now());
        return users.saveAndFlush(user);
    }

    private String login(User user) throws Exception {
        var result = mvc.perform(post("/api/auth/login").contextPath("/api").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return Bearer.tokenFrom(result.getResponse().getContentAsString());
    }

    private ResultActions call(Endpoint endpoint, String token) throws Exception {
        MockHttpServletRequestBuilder builder = request(endpoint.method(), "/api" + endpoint.path()).contextPath("/api");
        if (endpoint.body() != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(endpoint.body());
        }
        return mvc.perform(builder.with(Bearer.of(token)));
    }

    private static SecretKey key(String secret) {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    private static String forged(String secret, String issuer, String email, String role, Instant expiresAt) {
        return Jwts.builder().issuer(issuer).subject(email).claim("role", role).issuedAt(Date.from(Instant.now().minusSeconds(7200)))
                .expiration(Date.from(expiresAt)).signWith(key(secret), Jwts.SIG.HS256).compact();
    }

    // --- 401: sin token o con token inválido ---------------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @MethodSource("allProtected")
    void everyProtectedEndpointReturns401WithoutToken(Endpoint endpoint) throws Exception {
        call(endpoint, null).andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value(MISSING_TOKEN))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    void invalidTokensReturn401WithTheInvalidTokenMessage() throws Exception {
        User known = account(Role.COACH, UserStatus.ACTIVO);
        String valid = login(known);
        String tamperedSignature = valid.substring(0, valid.length() - 2)
                + (valid.endsWith("AA") ? "BB" : "AA");
        List<String> tokens = List.of(
                "basura",
                "a.b.c",
                tamperedSignature,
                forged(TestJwt.SECRET, "kodikalab", known.getEmail(), "COACH", Instant.now().minusSeconds(3600)),
                forged("otra-clave-distinta-de-al-menos-32-caracteres!!", "kodikalab", known.getEmail(), "COACH",
                        Instant.now().plusSeconds(3600)),
                forged(TestJwt.SECRET, "otro-emisor", known.getEmail(), "COACH", Instant.now().plusSeconds(3600)),
                forged(TestJwt.SECRET, "kodikalab", "no.existe@gmail.com", "COACH", Instant.now().plusSeconds(3600)));
        for (String token : tokens) {
            mvc.perform(get("/api/teams").contextPath("/api").with(Bearer.of(token)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("invalid_token")))
                    .andExpect(jsonPath("$.message").value(INVALID_TOKEN));
        }
    }

    @Test
    void tokenOfADeletedAccountGets401() throws Exception {
        User user = account(Role.PRACTICANTE, UserStatus.ACTIVO);
        String token = login(user);
        mvc.perform(get("/api/teams").contextPath("/api").with(Bearer.of(token))).andExpect(status().isOk());
        jdbc.update("DELETE FROM usuario WHERE id = ?", user.getId());
        mvc.perform(get("/api/teams").contextPath("/api").with(Bearer.of(token)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value(INVALID_TOKEN));
    }

    @Test
    void unknownRoutesAre401WithoutTokenAnd404WithIt() throws Exception {
        String token = login(account(Role.COACH, UserStatus.ACTIVO));
        mvc.perform(get("/api/no-existe").contextPath("/api")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/no-existe").contextPath("/api").with(Bearer.of(token))).andExpect(status().isNotFound());
    }

    // --- 403: rol sin permiso ---------------------------------------------------------------------------------------

    @ParameterizedTest(name = "practicante: {0}")
    @MethodSource("coachOnly")
    void practitionerCannotUseCoachEndpoints(Endpoint endpoint) throws Exception {
        String token = login(account(Role.PRACTICANTE, UserStatus.ACTIVO));
        call(endpoint, token).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(FORBIDDEN)).andExpect(jsonPath("$.errors").isEmpty());
    }

    @ParameterizedTest(name = "coach: {0}")
    @MethodSource("practitionerOnly")
    void coachCannotUsePractitionerEndpoints(Endpoint endpoint) throws Exception {
        String token = login(account(Role.COACH, UserStatus.ACTIVO));
        call(endpoint, token).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(FORBIDDEN)).andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    void theRightRoleGetsPastTheSecurityGateAndReachesTheController() throws Exception {
        String coach = login(account(Role.COACH, UserStatus.ACTIVO));
        String practitioner = login(account(Role.PRACTICANTE, UserStatus.ACTIVO));
        // Un coach con cuerpo vacío llega a la validación de US-04 (400), no al rechazo de seguridad.
        call(new Endpoint(HttpMethod.POST, "/teams", "{}"), coach).andExpect(status().isBadRequest());
        // Cualquier cuenta autenticada puede listar grupos y el catálogo.
        for (String token : List.of(coach, practitioner)) {
            call(new Endpoint(HttpMethod.GET, "/teams", null), token).andExpect(status().isOk());
            call(new Endpoint(HttpMethod.GET, "/problems", null), token).andExpect(status().isOk());
        }
        // Un practicante en su endpoint llega al servicio: el rechazo ya no es el genérico de seguridad.
        call(new Endpoint(HttpMethod.POST, "/teams/999999/join", null), practitioner)
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(FORBIDDEN)));
    }

    @Test
    void roleClaimInsideTheTokenIsNotTrusted() throws Exception {
        User practitioner = account(Role.PRACTICANTE, UserStatus.ACTIVO);
        String claimsCoach = forged(TestJwt.SECRET, "kodikalab", practitioner.getEmail(), "COACH",
                Instant.now().plusSeconds(3600));
        call(new Endpoint(HttpMethod.POST, "/teams", "{}"), claimsCoach)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value(FORBIDDEN));
    }

    @Test
    void suspendedAccountWithAValidTokenGets403OnEveryEndpoint() throws Exception {
        User user = account(Role.COACH, UserStatus.ACTIVO);
        String token = login(user);
        jdbc.update("UPDATE usuario SET estado_cuenta = 'SUSPENDIDO' WHERE id = ?", user.getId());
        for (Endpoint endpoint : List.of(new Endpoint(HttpMethod.GET, "/teams", null),
                new Endpoint(HttpMethod.POST, "/teams", "{}"), new Endpoint(HttpMethod.GET, "/users/me", null))) {
            call(endpoint, token).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("La cuenta no está habilitada"));
        }
    }

    // --- Login y sesión ---------------------------------------------------------------------------------------------

    @Test
    void loginIssuesABearerTokenThatWorksAndCreatesNoSessionOrCookie() throws Exception {
        User user = account(Role.COACH, UserStatus.ACTIVO);
        var result = mvc.perform(post("/api/auth/login").contextPath("/api").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("COACH"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(86400))
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE)).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();

        String token = Bearer.tokenFrom(result.getResponse().getContentAsString());
        var authenticated = mvc.perform(get("/api/teams").contextPath("/api").with(Bearer.of(token)))
                .andExpect(status().isOk()).andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE)).andReturn();
        assertThat(authenticated.getRequest().getSession(false)).isNull();
    }

    @Test
    void publicEndpointsIgnoreAnInvalidTokenTheClientAttaches() throws Exception {
        User user = account(Role.PRACTICANTE, UserStatus.ACTIVO);
        mvc.perform(post("/api/auth/login").contextPath("/api").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-viejo-o-roto")
                        .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").isString());
        mvc.perform(post("/api/auth/login").contextPath("/api").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"Incorrecta1\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Credenciales inválidas"));
    }

    // --- Documentación y CORS ---------------------------------------------------------------------------------------

    @Test
    void openApiIsPublicAndDeclaresTheBearerScheme() throws Exception {
        mvc.perform(get("/api/v3/api-docs").contextPath("/api")).andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("KodikaLab API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/auth/login'].post.security").value(empty()))
                .andExpect(jsonPath("$.paths['/auth/register'].post.security").value(empty()))
                .andExpect(jsonPath("$.tags[*].name").value(hasItem("Equipos")));
    }

    @Test
    void swaggerUiIsPublic() throws Exception {
        mvc.perform(get("/api/swagger-ui.html").contextPath("/api")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/api/swagger-ui/index.html").contextPath("/api")).andExpect(status().isOk());
    }

    @Test
    void corsPreflightIsAnsweredOnlyForConfiguredOrigins() throws Exception {
        mvc.perform(options("/api/teams").contextPath("/api").header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("POST")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsStringIgnoringCase("authorization")))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
        mvc.perform(options("/api/teams").contextPath("/api").header(HttpHeaders.ORIGIN, "http://sitio-ajeno.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedResponsesToAllowedOriginsCarryTheCorsHeader() throws Exception {
        String token = login(account(Role.PRACTICANTE, UserStatus.ACTIVO));
        mvc.perform(get("/api/teams").contextPath("/api").header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .with(Bearer.of(token)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, startsWith("http://localhost:3000")));
    }
}

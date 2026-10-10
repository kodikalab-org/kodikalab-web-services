package com.kodika.kodikalab.auth;

import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserRepository;
import com.kodika.kodikalab.users.UserStatus;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.*;

/** HTTP, cookies, Spring Security and PostgreSQL in an isolated schema; never uses the development database. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "LOGIN_TEST_DB_URL", matches = ".+")
@Import(LoginIntegrationTests.ProbeConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LoginIntegrationTests {
    private static final String SCHEMA = "login_test_" + UUID.randomUUID().toString().replace("-", "");
    private static boolean schemaCreated;
    @Autowired TestRestTemplate http;
    @Autowired UserRepository repository;
    @Autowired PasswordEncoder encoder;

    private static Connection connect() throws SQLException {
        String url = System.getenv("LOGIN_TEST_DB_URL");
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("Configure LOGIN_TEST_DB_URL and test database credentials. "
                    + "Do not force disabled tests in IntelliJ without these variables.");
        }
        return DriverManager.getConnection(url,
                System.getenv().getOrDefault("LOGIN_TEST_DB_USER", "postgres"),
                System.getenv().getOrDefault("LOGIN_TEST_DB_PASSWORD", ""));
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) throws SQLException {
        try (Connection connection = connect(); var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + SCHEMA);
            schemaCreated = true;
        }
        registry.add("spring.datasource.url", () -> System.getenv("LOGIN_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("LOGIN_TEST_DB_USER", "postgres"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("LOGIN_TEST_DB_PASSWORD", ""));
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA);
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("server.servlet.session.cookie.secure", () -> "false");
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
        user.setEmail("test.login." + UUID.randomUUID() + "@gmail.com");
        user.setPasswordHash(encoder.encode("Password123"));
        user.setRole(role);
        user.setStatus(status);
        user.setCreatedAt(OffsetDateTime.now());
        return repository.saveAndFlush(user);
    }

    private ResponseEntity<Map<String, Object>> login(String email, String password, String cookie) {
        return post(Map.of("email", email, "password", password), cookie);
    }

    private ResponseEntity<Map<String, Object>> post(Map<String, String> body, String cookie) {
        HttpHeaders headers = new HttpHeaders();
        if (cookie != null) headers.set(HttpHeaders.COOKIE, cookie);
        // TestRestTemplate already adds the /api context path.
        return http.exchange("/auth/login", HttpMethod.POST, new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    private Map<String, Object> probe(String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, cookie);
        var response = http.exchange("/test/session", HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<Map<String, Object>>() {});
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return response.getBody();
    }

    private String cookie(ResponseEntity<?> response) {
        String value = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(value).isNotNull();
        return value.split(";", 2)[0];
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void authenticatesEachStoredRoleAndPersistsSessionAcrossRequests(Role role) {
        User user = account(role, UserStatus.ACTIVO);
        var response = login("  " + user.getEmail().toUpperCase() + "  ", "Password123", null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsEntry("message", "Inicio de sesión exitoso")
                .containsEntry("email", user.getEmail()).containsEntry("role", role.name())
                .doesNotContainKeys("password", "passwordHash", "password_hash", "token");
        String header = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(header).contains("JSESSIONID=", "HttpOnly", "SameSite=Lax");
        assertThat(probe(cookie(response))).containsEntry("email", user.getEmail())
                .containsEntry("authenticated", true).containsEntry("authorities", List.of("ROLE_" + role.name()));
        assertThat(repository.findById(user.getId()).orElseThrow().getPasswordHash()).isEqualTo(user.getPasswordHash());
        assertSqlValues(user, role.name(), UserStatus.ACTIVO.name());
    }

    @Test
    void unknownEmailAndWrongPasswordHaveSameGenericErrorAndNoNewSession() {
        User user = account(Role.PRACTICANTE, UserStatus.ACTIVO);
        var wrong = login(user.getEmail(), "Wrong123", null);
        var unknown = login("test.missing." + UUID.randomUUID() + "@gmail.com", "Wrong123", null);
        assertThat(wrong.getStatusCode().value()).isEqualTo(401);
        assertThat(unknown.getStatusCode().value()).isEqualTo(401);
        assertThat(wrong.getBody()).containsEntry("message", "Credenciales inválidas");
        assertThat(unknown.getBody()).isEqualTo(wrong.getBody());
        assertThat(wrong.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(unknown.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"SUSPENDIDO"})
    void disabledAccountCannotCreateSession(UserStatus status) {
        User user = account(Role.COACH, status);
        var response = login(user.getEmail(), "Password123", null);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody()).containsEntry("message", "Credenciales inválidas");
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(repository.findById(user.getId()).orElseThrow().getStatus()).isEqualTo(status);
        assertSqlValues(user, "COACH", "SUSPENDIDO");
    }

    @Test
    void successfulReauthenticationRotatesSessionIdAndInvalidatesOldId() {
        User user = account(Role.COACH, UserStatus.ACTIVO);
        String previous = cookie(login(user.getEmail(), "Password123", null));
        String current = cookie(login(user.getEmail(), "Password123", previous));
        assertThat(current).isNotEqualTo(previous);
        assertThat(probe(current)).containsEntry("authenticated", true).containsEntry("email", user.getEmail());
        assertThat(probe(previous)).containsEntry("authenticated", false);
    }

    @Test
    void malformedEmailAndMissingPasswordReturn400WithoutSession() {
        var invalidEmail = login("not-an-email", "Password123", null);
        var missingPassword = post(Map.of("email", "test@gmail.com"), null);
        assertThat(invalidEmail.getStatusCode().value()).isEqualTo(400);
        assertThat(missingPassword.getStatusCode().value()).isEqualTo(400);
        assertThat(invalidEmail.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(missingPassword.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    void oversizedPasswordIsNotTruncatedToAValidCredential() {
        User user = account(Role.PRACTICANTE, UserStatus.ACTIVO);
        user.setPasswordHash(encoder.encode("A1" + "a".repeat(70)));
        repository.saveAndFlush(user);
        var response = login(user.getEmail(), "A1" + "a".repeat(71), null);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    void clientCannotOverrideStoredRole() {
        User user = account(Role.PRACTICANTE, UserStatus.ACTIVO);
        var response = post(Map.of("email", user.getEmail(), "password", "Password123", "role", "ADMIN"), null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsEntry("role", "PRACTICANTE");
        assertThat(probe(cookie(response))).containsEntry("authorities", List.of("ROLE_PRACTICANTE"));
    }

    @Test
    void failedAttemptDoesNotPreventRetryWithCorrectPassword() {
        User user = account(Role.PRACTICANTE, UserStatus.ACTIVO);
        assertThat(login(user.getEmail(), "Wrong123", null).getStatusCode().value()).isEqualTo(401);
        assertThat(login(user.getEmail(), "Password123", null).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void plaintextFixtureIsNotAcceptedAsPasswordHash() {
        User user = account(Role.COACH, UserStatus.ACTIVO);
        user.setPasswordHash("SOLO_PRUEBAS_NO_LOGIN");
        repository.saveAndFlush(user);
        assertThat(login(user.getEmail(), "SOLO_PRUEBAS_NO_LOGIN", null).getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void failedLoginDoesNotReplacePreviouslyAuthenticatedIdentity() {
        User user = account(Role.COACH, UserStatus.ACTIVO);
        String existing = cookie(login(user.getEmail(), "Password123", null));
        var failed = login(user.getEmail(), "Wrong123", existing);
        assertThat(failed.getStatusCode().value()).isEqualTo(401);
        assertThat(failed.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(probe(existing)).containsEntry("email", user.getEmail()).containsEntry("authenticated", true);
    }

    private void assertSqlValues(User user, String role, String status) {
        try (Connection connection = connect(); var statement = connection.prepareStatement(
                "SELECT nombre_completo, rol, estado_cuenta FROM " + SCHEMA + ".usuario WHERE id = ?")) {
            statement.setInt(1, user.getId());
            try (var row = statement.executeQuery()) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString("nombre_completo")).isEqualTo("Usuario Prueba");
                assertThat(row.getString("rol")).isEqualTo(role);
                assertThat(row.getString("estado_cuenta")).isEqualTo(status);
            }
        } catch (SQLException exception) {
            throw new AssertionError("No se pudo verificar la persistencia oficial", exception);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfiguration {
        @Bean
        ProbeController sessionProbeController() {
            return new ProbeController();
        }
    }

    /** Test-only endpoint to verify Spring Security reloads the saved context, never part of the production JAR. */
    @TestComponent
    @RestController
    static class ProbeController {
        @GetMapping("/test/session")
        Map<String, Object> session(Authentication authentication) {
            if (authentication == null) return Map.of("authenticated", false);
            return Map.of("authenticated", authentication.isAuthenticated(), "email", authentication.getName(),
                    "authorities", authentication.getAuthorities().stream().map(authority -> authority.getAuthority()).toList());
        }
    }
}

package com.kodika.kodikalab.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kodika.kodikalab.support.Bearer;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserRepository;
import com.kodika.kodikalab.users.UserStatus;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Recuperación de acceso sin correo (US-02, escenario alternativo) de punta a punta: HTTP, Spring Security y
 * PostgreSQL en un schema aislado. Nunca usa la base de desarrollo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "RECOVERY_TEST_DB_URL", matches = ".+")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RecoveryIntegrationTests {
    private static final String SCHEMA = "recovery_test_" + UUID.randomUUID().toString().replace("-", "");
    private static final String CODE_FORMAT = "^[A-Z2-7]{4}(-[A-Z2-7]{4}){5}$";
    private static final String INVALID = "Datos de recuperación inválidos";
    private static boolean schemaCreated;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    private final ObjectMapper mapper = new ObjectMapper();

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(System.getenv("RECOVERY_TEST_DB_URL"),
                System.getenv().getOrDefault("RECOVERY_TEST_DB_USER", "postgres"),
                System.getenv().getOrDefault("RECOVERY_TEST_DB_PASSWORD", ""));
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) throws SQLException {
        try (Connection connection = connect(); var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + SCHEMA);
            schemaCreated = true;
        }
        registry.add("spring.datasource.url", () -> System.getenv("RECOVERY_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("RECOVERY_TEST_DB_USER", "postgres"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("RECOVERY_TEST_DB_PASSWORD", ""));
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA);
        registry.add("spring.jpa.show-sql", () -> "false");
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        if (schemaCreated) {
            try (Connection connection = connect(); var statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
            }
        }
    }

    private static String newEmail() {
        return "test.recovery." + UUID.randomUUID() + "@gmail.com";
    }

    private MvcResult send(String path, String json, String token) throws Exception {
        return mvc.perform(post("/api" + path).contextPath("/api").contentType(MediaType.APPLICATION_JSON).content(json)
                .with(Bearer.of(token))).andReturn();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private String register(String email, String password) throws Exception {
        MvcResult result = send("/auth/register", "{\"firstName\":\"Usuario\",\"lastName\":\"Prueba\",\"email\":\""
                + email + "\",\"password\":\"" + password + "\",\"role\":\"PRACTICANTE\"}", null);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return json(result).get("recoveryCode").asText();
    }

    private MvcResult login(String email, String password) throws Exception {
        return send("/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}", null);
    }

    private String tokenOf(MvcResult login) throws Exception {
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        return json(login).get("token").asText();
    }

    private MvcResult recover(String email, String code, String newPassword) throws Exception {
        return send("/auth/recovery", "{\"email\":\"" + email + "\",\"recoveryCode\":\"" + code + "\",\"newPassword\":\""
                + newPassword + "\"}", null);
    }

    private int teamsStatus(String token) throws Exception {
        return mvc.perform(get("/api/teams").contextPath("/api").with(Bearer.of(token))).andReturn().getResponse().getStatus();
    }

    private String storedHash(String email) {
        return jdbc.queryForObject("SELECT password_hash FROM usuario WHERE correo = ?", String.class, email);
    }

    @Test
    void registrationReturnsARecoveryCodeInTheExpectedFormatAndNothingExtraIsStored() throws Exception {
        String email = newEmail();
        MvcResult result = send("/auth/register", "{\"firstName\":\"Usuario\",\"lastName\":\"Prueba\",\"email\":\""
                + email + "\",\"password\":\"Password123\",\"role\":\"COACH\"}", null);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = json(result);
        assertThat(body.get("recoveryCode").asText()).matches(CODE_FORMAT);
        assertThat(body.has("token")).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema = ? "
                + "AND table_name = 'usuario'", Integer.class, SCHEMA)).isEqualTo(7);
    }

    @Test
    void fullRecoveryFlowChangesThePasswordAndInvalidatesEverythingIssuedBefore() throws Exception {
        String email = newEmail();
        String firstCode = register(email, "Password123");
        String oldToken = tokenOf(login(email, "Password123"));
        assertThat(teamsStatus(oldToken)).isEqualTo(200);
        String oldHash = storedHash(email);

        MvcResult recovered = recover(email, firstCode.toLowerCase(), "Nueva1234");
        assertThat(recovered.getResponse().getStatus()).isEqualTo(200);
        String secondCode = json(recovered).get("recoveryCode").asText();
        assertThat(secondCode).matches(CODE_FORMAT).isNotEqualTo(firstCode);
        assertThat(json(recovered).get("message").asText()).contains("Contraseña actualizada");

        // La contraseña se guardó con BCrypt y es otra.
        assertThat(storedHash(email)).startsWith("$2").isNotEqualTo(oldHash);
        assertThat(encoder.matches("Nueva1234", storedHash(email))).isTrue();
        // Nada emitido antes sigue vigente.
        assertThat(teamsStatus(oldToken)).isEqualTo(401);
        assertThat(login(email, "Password123").getResponse().getStatus()).isEqualTo(401);
        assertThat(recover(email, firstCode, "Otra12345").getResponse().getStatus()).isEqualTo(401);
        // Se habilita un nuevo intento de inicio de sesión con las credenciales nuevas.
        String newToken = tokenOf(login(email, "Nueva1234"));
        assertThat(teamsStatus(newToken)).isEqualTo(200);
        // El código nuevo sirve una vez más y rota otra vez.
        MvcResult again = recover(email, secondCode, "Tercera1234");
        assertThat(again.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(again).get("recoveryCode").asText()).isNotEqualTo(secondCode);
        assertThat(teamsStatus(newToken)).isEqualTo(401);
    }

    @Test
    void unknownEmailWrongCodeAndSuspendedAccountAreIndistinguishable() throws Exception {
        String email = newEmail();
        String code = register(email, "Password123");
        String suspended = newEmail();
        String suspendedCode = register(suspended, "Password123");
        jdbc.update("UPDATE usuario SET estado_cuenta = 'SUSPENDIDO' WHERE correo = ?", suspended);

        MvcResult wrong = recover(email, "AAAA-BBBB-CCCC-DDDD-EEEE-FFFF", "Nueva1234");
        MvcResult unknown = recover(newEmail(), code, "Nueva1234");
        MvcResult disabled = recover(suspended, suspendedCode, "Nueva1234");
        for (MvcResult result : List.of(wrong, unknown, disabled)) {
            assertThat(result.getResponse().getStatus()).isEqualTo(401);
            assertThat(json(result).get("message").asText()).isEqualTo(INVALID);
            assertThat(json(result).has("recoveryCode")).isFalse();
        }
        assertThat(unknown.getResponse().getContentAsString()).isEqualTo(wrong.getResponse().getContentAsString());
        assertThat(disabled.getResponse().getContentAsString()).isEqualTo(wrong.getResponse().getContentAsString());
        // Nada cambió: la contraseña de la cuenta vigente sigue siendo la original.
        assertThat(login(email, "Password123").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void invalidNewPasswordsAreRejectedAndTheCodeIsNotConsumed() throws Exception {
        String email = newEmail();
        String code = register(email, "Password123");
        for (String weak : new String[]{"12345", "password123", "Password", "A1" + "ñ".repeat(36)}) {
            assertThat(recover(email, code, weak).getResponse().getStatus()).isEqualTo(400);
        }
        assertThat(login(email, "Password123").getResponse().getStatus()).isEqualTo(200);
        assertThat(recover(email, code, "Nueva1234").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void concurrentRecoveriesWithTheSameCodeLetExactlyOneWin() throws Exception {
        String email = newEmail();
        String code = register(email, "Password123");
        int attempts = 6;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        try {
            List<Callable<Integer>> tasks = new ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                String password = "Nueva" + i + "Clave";
                tasks.add(() -> recover(email, code, password).getResponse().getStatus());
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : pool.invokeAll(tasks)) {
                statuses.add(future.get());
            }
            assertThat(statuses.stream().filter(status -> status == 200)).hasSize(1);
            assertThat(statuses.stream().filter(status -> status == 401)).hasSize(attempts - 1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void recoveryCodeEndpointRequiresTheTokenAndTheCurrentPassword() throws Exception {
        String email = newEmail();
        String code = register(email, "Password123");
        String token = tokenOf(login(email, "Password123"));

        MvcResult anonymous = send("/auth/recovery-code", "{\"password\":\"Password123\"}", null);
        assertThat(anonymous.getResponse().getStatus()).isEqualTo(401);
        assertThat(json(anonymous).get("message").asText()).startsWith("Debe iniciar sesión");
        MvcResult wrong = send("/auth/recovery-code", "{\"password\":\"Wrong1234\"}", token);
        assertThat(wrong.getResponse().getStatus()).isEqualTo(401);
        assertThat(json(wrong).get("message").asText()).isEqualTo("Credenciales inválidas");
        assertThat(send("/auth/recovery-code", "{}", token).getResponse().getStatus()).isEqualTo(400);

        MvcResult shown = send("/auth/recovery-code", "{\"password\":\"Password123\"}", token);
        assertThat(shown.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(shown).get("recoveryCode").asText()).isEqualTo(code);
        assertThat(shown.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
    }

    @Test
    void accountsCreatedBeforeThisFeatureCanFetchTheirCodeAndRecover() throws Exception {
        User old = new User();
        old.setFullName("Cuenta Antigua");
        old.setEmail(newEmail());
        old.setPasswordHash(encoder.encode("Password123"));
        old.setRole(Role.COACH);
        old.setStatus(UserStatus.ACTIVO);
        old.setCreatedAt(OffsetDateTime.now());
        users.saveAndFlush(old);

        String token = tokenOf(login(old.getEmail(), "Password123"));
        String code = json(send("/auth/recovery-code", "{\"password\":\"Password123\"}", token)).get("recoveryCode").asText();
        assertThat(code).matches(CODE_FORMAT);
        assertThat(recover(old.getEmail(), code, "Nueva1234").getResponse().getStatus()).isEqualTo(200);
        assertThat(login(old.getEmail(), "Nueva1234").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void recoveryIsPublicAndIgnoresAnInvalidTokenTheClientAttaches() throws Exception {
        MvcResult result = send("/auth/recovery", "{\"email\":\"" + newEmail()
                + "\",\"recoveryCode\":\"AAAA-BBBB-CCCC-DDDD-EEEE-FFFF\",\"newPassword\":\"Nueva1234\"}", "token-roto");
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(json(result).get("message").asText()).isEqualTo(INVALID);
    }

    @Test
    void aSuspendedAccountLosesItsOldTokenAndCannotUseRecoveryToGetBackIn() throws Exception {
        String email = newEmail();
        String code = register(email, "Password123");
        String token = tokenOf(login(email, "Password123"));
        jdbc.update("UPDATE usuario SET estado_cuenta = 'SUSPENDIDO' WHERE correo = ?", email);
        assertThat(teamsStatus(token)).isEqualTo(403);
        assertThat(recover(email, code, "Nueva1234").getResponse().getStatus()).isEqualTo(401);
        assertThat(login(email, "Password123").getResponse().getStatus()).isEqualTo(401);
    }
}

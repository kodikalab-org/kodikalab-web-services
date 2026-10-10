package com.kodika.kodikalab.profiles;

import com.kodika.kodikalab.profiles.practitioner.integration.CodeforcesClient;
import com.kodika.kodikalab.profiles.practitioner.integration.CodeforcesUserInfo;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserRepository;
import com.kodika.kodikalab.users.UserStatus;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US-03 de punta a punta: HTTP, cookie de sesión, Spring Security y PostgreSQL en un schema aislado.
 * Codeforces se reemplaza por un stub para no depender de internet. Nunca usa la base de desarrollo.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "PROFILE_TEST_DB_URL", matches = ".+")
@Import(ProfileIntegrationTests.CodeforcesStubConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProfileIntegrationTests {
    private static final String SCHEMA = "profile_test_" + UUID.randomUUID().toString().replace("-", "");
    private static final String PASSWORD = "Password123";
    private static boolean schemaCreated;
    @Autowired TestRestTemplate http;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    private static Connection connect() throws SQLException {
        String url = System.getenv("PROFILE_TEST_DB_URL");
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("Configure PROFILE_TEST_DB_URL and test database credentials. "
                    + "Do not force disabled tests in IntelliJ without these variables.");
        }
        return DriverManager.getConnection(url,
                System.getenv().getOrDefault("PROFILE_TEST_DB_USER", "postgres"),
                System.getenv().getOrDefault("PROFILE_TEST_DB_PASSWORD", ""));
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) throws SQLException {
        try (Connection connection = connect(); var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + SCHEMA);
            schemaCreated = true;
        }
        registry.add("spring.datasource.url", () -> System.getenv("PROFILE_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("PROFILE_TEST_DB_USER", "postgres"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("PROFILE_TEST_DB_PASSWORD", ""));
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

    // ---------- Practicante ----------

    @Test
    void practitionerCreatesReadsAndUpdatesOwnProfile() {
        User user = account(Role.PRACTICANTE);
        String session = login(user);

        var missing = get(session);
        assertThat(missing.getStatusCode().value()).isEqualTo(404);
        assertThat(missing.getBody()).containsEntry("message", "El perfil de practicante aún no está registrado");

        var created = put(session, practitionerBody(code(), "tourist"));
        assertThat(created.getStatusCode().value()).isEqualTo(200);
        assertThat(created.getBody()).containsEntry("message", "Perfil actualizado correctamente");
        Map<String, Object> profile = profile(created);
        assertThat(profile).containsEntry("email", user.getEmail()).containsEntry("role", "PRACTICANTE")
                .containsEntry("codeforcesHandle", "tourist").containsEntry("codeforcesRating", 3800)
                .doesNotContainKeys("passwordHash", "usuarioId", "leetcodeHandle");

        Map<String, Object> row = practitionerRow(user.getId()).orElseThrow();
        assertThat(row).containsEntry("carrera", "Ingeniería de Software").containsEntry("ciclo_academico", 5)
                .containsEntry("nivel_competitivo", "INTERMEDIO").containsEntry("codeforces_rating", 3800);

        Map<String, Object> update = practitionerBody(String.valueOf(row.get("codigo_estudiante")), "tourist");
        update.put("carrera", "Ciencias de la Computación");
        update.put("nivelCompetitivo", "AVANZADO");
        assertThat(put(session, update).getStatusCode().value()).isEqualTo(200);

        assertThat(count("practicante", user.getId())).isEqualTo(1);
        assertThat(practitionerRow(user.getId()).orElseThrow())
                .containsEntry("carrera", "Ciencias de la Computación").containsEntry("nivel_competitivo", "AVANZADO");
        assertThat(profile(get(session))).containsEntry("carrera", "Ciencias de la Computación");
        assertRoleUnchanged(user, "PRACTICANTE");
    }

    @Test
    void invalidPractitionerDataReturns400AndKeepsPreviousData() {
        User user = account(Role.PRACTICANTE);
        String session = login(user);
        String code = code();
        put(session, practitionerBody(code, "tourist"));

        Map<String, Object> invalid = practitionerBody("", "handle con espacios");
        invalid.put("cicloAcademico", 0);
        var response = put(session, invalid);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(errors(response)).containsKeys("codigoEstudiante", "cicloAcademico", "codeforcesHandle");
        assertThat(practitionerRow(user.getId()).orElseThrow()).containsEntry("codigo_estudiante", code)
                .containsEntry("ciclo_academico", 5).containsEntry("codeforces_handle", "tourist");
    }

    @Test
    void unconfirmedCodeforcesHandleSavesOtherDataAndKeepsPreviousCodeforcesValues() {
        User user = account(Role.PRACTICANTE);
        String session = login(user);
        String code = code();

        var withoutPrevious = put(session, practitionerBody(code, "missing_handle"));
        assertThat(withoutPrevious.getStatusCode().value()).isEqualTo(200);
        assertThat((String) withoutPrevious.getBody().get("message"))
                .contains("Codeforces no confirmó el identificador informado");
        assertThat(practitionerRow(user.getId()).orElseThrow())
                .containsEntry("codeforces_handle", null).containsEntry("codeforces_rating", null);

        put(session, practitionerBody(code, "tourist"));
        Map<String, Object> change = practitionerBody(code, "missing_handle");
        change.put("carrera", "Ingeniería de Sistemas");
        put(session, change);

        assertThat(practitionerRow(user.getId()).orElseThrow())
                .containsEntry("carrera", "Ingeniería de Sistemas")
                .containsEntry("codeforces_handle", "tourist").containsEntry("codeforces_rating", 3800);
    }

    @Test
    void codeforcesUserWithoutRatingIsStoredWithNullRatingAndBlankHandleClearsCodeforces() {
        User user = account(Role.PRACTICANTE);
        String session = login(user);
        String code = code();

        put(session, practitionerBody(code, "newbie_ok"));
        assertThat(practitionerRow(user.getId()).orElseThrow())
                .containsEntry("codeforces_handle", "newbie_ok").containsEntry("codeforces_rating", null);

        put(session, practitionerBody(code, "  "));
        assertThat(practitionerRow(user.getId()).orElseThrow())
                .containsEntry("codeforces_handle", null).containsEntry("codeforces_rating", null);
    }

    @Test
    void duplicateStudentCodeReturns409() {
        String code = code();
        String first = login(account(Role.PRACTICANTE));
        String second = login(account(Role.PRACTICANTE));
        assertThat(put(first, practitionerBody(code, null)).getStatusCode().value()).isEqualTo(200);

        var duplicate = put(second, practitionerBody(code, null));

        assertThat(duplicate.getStatusCode().value()).isEqualTo(409);
        assertThat(duplicate.getBody()).containsEntry("message",
                "El código de estudiante ya está vinculado a otro practicante");
    }

    @Test
    void concurrentDuplicateStudentCodeOnlyPersistsOneProfile() throws Exception {
        String code = code();
        List<String> sessions = List.of(login(account(Role.PRACTICANTE)), login(account(Role.PRACTICANTE)),
                login(account(Role.PRACTICANTE)), login(account(Role.PRACTICANTE)));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(sessions.size());
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (String session : sessions) {
                Callable<Integer> call = () -> {
                    start.await();
                    return put(session, practitionerBody(code, null)).getStatusCode().value();
                };
                results.add(pool.submit(call));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
            assertThat(statuses).containsOnly(200, 409).filteredOn(status -> status == 200).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(scalar("SELECT COUNT(*) FROM " + SCHEMA + ".practicante WHERE codigo_estudiante = ?", code))
                .isEqualTo(1L);
    }

    @Test
    void bodyCannotChooseTargetUserOrRoleAndLeetcodeIsIgnored() {
        User victim = account(Role.PRACTICANTE);
        User user = account(Role.PRACTICANTE);
        String session = login(user);
        Map<String, Object> body = practitionerBody(code(), null);
        body.put("usuarioId", victim.getId());
        body.put("userId", victim.getId());
        body.put("role", "COACH");
        body.put("leetcodeHandle", "leet_user");

        var response = put(session, body);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(profile(response)).containsEntry("email", user.getEmail()).containsEntry("role", "PRACTICANTE")
                .doesNotContainKey("leetcodeHandle");
        assertThat(count("practicante", user.getId())).isEqualTo(1);
        assertThat(count("practicante", victim.getId())).isZero();
        assertRoleUnchanged(user, "PRACTICANTE");
    }

    // ---------- Coach ----------

    @Test
    void coachCreatesReadsAndUpdatesOwnProfile() {
        User user = account(Role.COACH);
        String session = login(user);

        var missing = get(session);
        assertThat(missing.getStatusCode().value()).isEqualTo(404);
        assertThat(missing.getBody()).containsEntry("message", "El perfil de coach aún no está registrado");

        var created = put(session, coachBody("Grafos", 4));
        assertThat(created.getStatusCode().value()).isEqualTo(200);
        assertThat(profile(created)).containsEntry("email", user.getEmail()).containsEntry("role", "COACH")
                .containsEntry("especialidadPrincipal", "Grafos").containsEntry("aniosExperiencia", 4)
                .doesNotContainKeys("passwordHash", "usuarioId", "codigoEstudiante");
        assertThat(coachRow(user.getId()).orElseThrow())
                .containsEntry("especialidad_principal", "Grafos")
                .containsEntry("organizacion_club", "Club de Programación Competitiva")
                .containsEntry("anios_experiencia", 4);

        put(session, coachBody("Flujo en redes", 6));

        assertThat(count("coach", user.getId())).isEqualTo(1);
        assertThat(coachRow(user.getId()).orElseThrow())
                .containsEntry("especialidad_principal", "Flujo en redes").containsEntry("anios_experiencia", 6);
        assertThat(profile(get(session))).containsEntry("especialidadPrincipal", "Flujo en redes");
        assertRoleUnchanged(user, "COACH");
    }

    @Test
    void invalidCoachDataReturns400AndKeepsPreviousData() {
        User user = account(Role.COACH);
        String session = login(user);
        put(session, coachBody("Grafos", 4));

        Map<String, Object> invalid = coachBody(" ", -1);
        invalid.put("presentacion", "x".repeat(501));
        invalid.put("organizacionClub", "y".repeat(151));
        var response = put(session, invalid);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(errors(response)).containsKeys("especialidadPrincipal", "aniosExperiencia", "presentacion",
                "organizacionClub");
        assertThat(coachRow(user.getId()).orElseThrow())
                .containsEntry("especialidad_principal", "Grafos").containsEntry("anios_experiencia", 4);
    }

    @Test
    void coachBoundaryValuesAndBlankOptionalsAreStored() {
        User user = account(Role.COACH);
        String session = login(user);
        Map<String, Object> body = coachBody("e".repeat(120), 60);
        body.put("organizacionClub", "   ");
        body.put("presentacion", "");

        assertThat(put(session, body).getStatusCode().value()).isEqualTo(200);
        assertThat(coachRow(user.getId()).orElseThrow())
                .containsEntry("organizacion_club", null).containsEntry("presentacion", null)
                .containsEntry("anios_experiencia", 60);

        assertThat(put(session, coachBody("Grafos", 61)).getStatusCode().value()).isEqualTo(400);
        assertThat(put(session, coachBody("e".repeat(121), 0)).getStatusCode().value()).isEqualTo(400);
    }

    // ---------- Reglas entre roles y sesión ----------

    @Test
    void eachRoleOnlyWritesItsOwnTable() {
        User coach = account(Role.COACH);
        User practitioner = account(Role.PRACTICANTE);
        String coachSession = login(coach);
        String practitionerSession = login(practitioner);

        var coachWithPractitionerBody = put(coachSession, practitionerBody(code(), "tourist"));
        var practitionerWithCoachBody = put(practitionerSession, coachBody("Grafos", 4));

        assertThat(coachWithPractitionerBody.getStatusCode().value()).isEqualTo(400);
        assertThat(errors(coachWithPractitionerBody)).containsKeys("especialidadPrincipal", "aniosExperiencia");
        assertThat(practitionerWithCoachBody.getStatusCode().value()).isEqualTo(400);
        assertThat(errors(practitionerWithCoachBody)).containsKey("codigoEstudiante");
        assertThat(count("practicante", coach.getId())).isZero();
        assertThat(count("coach", coach.getId())).isZero();
        assertThat(count("coach", practitioner.getId())).isZero();
        assertThat(count("practicante", practitioner.getId())).isZero();
    }

    @Test
    void requestsWithoutSessionReturn401() {
        var read = get(null);
        var write = put(null, coachBody("Grafos", 4));

        assertThat(read.getStatusCode().value()).isEqualTo(401);
        assertThat(write.getStatusCode().value()).isEqualTo(401);
        assertThat(read.getBody()).containsEntry("message", "Debe iniciar sesión para gestionar su perfil");
    }

    @Test
    void physicalSchemaMatchesErd() {
        assertColumn("practicante", "usuario_id", "integer", null, "NO");
        assertColumn("practicante", "codigo_estudiante", "character varying", 20, "NO");
        assertColumn("practicante", "carrera", "character varying", 100, "NO");
        assertColumn("practicante", "ciclo_academico", "integer", null, "NO");
        assertColumn("practicante", "nivel_competitivo", "character varying", 30, "NO");
        assertColumn("practicante", "codeforces_handle", "character varying", 50, "YES");
        assertColumn("practicante", "codeforces_rating", "integer", null, "YES");
        assertColumn("practicante", "atcoder_handle", "character varying", 50, "YES");
        assertColumn("practicante", "vjudge_handle", "character varying", 50, "YES");
        assertColumn("coach", "usuario_id", "integer", null, "NO");
        assertColumn("coach", "especialidad_principal", "character varying", 120, "NO");
        assertColumn("coach", "organizacion_club", "character varying", 150, "YES");
        assertColumn("coach", "anios_experiencia", "integer", null, "NO");
        assertColumn("coach", "presentacion", "character varying", 500, "YES");
        assertThat(scalar("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = ? "
                + "AND table_name = 'practicante'", SCHEMA)).isEqualTo(9L);
        assertThat(scalar("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = ? "
                + "AND table_name = 'coach'", SCHEMA)).isEqualTo(5L);
        for (String table : List.of("practicante", "coach")) {
            assertThat(scalar("""
                    SELECT COUNT(*) FROM information_schema.table_constraints tc
                    JOIN information_schema.key_column_usage k
                      ON tc.constraint_name = k.constraint_name AND tc.table_schema = k.table_schema
                    WHERE tc.table_schema = ? AND tc.table_name = ? AND k.column_name = 'usuario_id'
                      AND tc.constraint_type IN ('PRIMARY KEY', 'FOREIGN KEY')""", SCHEMA, table))
                    .as("usuario_id de %s debe ser PK y FK", table).isEqualTo(2L);
        }
    }

    // ---------- Utilidades ----------

    private User account(Role role) {
        User user = new User();
        user.setFullName("Usuario Prueba");
        user.setEmail("test.us03." + UUID.randomUUID() + "@gmail.com");
        user.setPasswordHash(encoder.encode(PASSWORD));
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVO);
        user.setCreatedAt(OffsetDateTime.now());
        return users.saveAndFlush(user);
    }

    private String login(User user) {
        var response = http.exchange("/auth/login", HttpMethod.POST,
                new HttpEntity<>(Map.of("email", user.getEmail(), "password", PASSWORD), jsonHeaders(null)),
                new ParameterizedTypeReference<Map<String, Object>>() {});
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(cookie).isNotNull();
        return cookie.split(";", 2)[0];
    }

    private ResponseEntity<Map<String, Object>> get(String session) {
        return http.exchange("/users/me", HttpMethod.GET, new HttpEntity<>(jsonHeaders(session)),
                new ParameterizedTypeReference<>() {});
    }

    private ResponseEntity<Map<String, Object>> put(String session, Map<String, Object> body) {
        return http.exchange("/users/me", HttpMethod.PUT, new HttpEntity<>(body, jsonHeaders(session)),
                new ParameterizedTypeReference<>() {});
    }

    private HttpHeaders jsonHeaders(String session) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (session != null) headers.set(HttpHeaders.COOKIE, session);
        return headers;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> profile(ResponseEntity<Map<String, Object>> response) {
        return (Map<String, Object>) response.getBody().get("profile");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> errors(ResponseEntity<Map<String, Object>> response) {
        return (Map<String, Object>) response.getBody().get("errors");
    }

    private static String code() {
        return "US03" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static Map<String, Object> practitionerBody(String code, String codeforcesHandle) {
        Map<String, Object> body = new HashMap<>();
        body.put("codigoEstudiante", code);
        body.put("carrera", "Ingeniería de Software");
        body.put("cicloAcademico", 5);
        body.put("nivelCompetitivo", "INTERMEDIO");
        body.put("codeforcesHandle", codeforcesHandle);
        body.put("atcoderHandle", "tourist_atcoder");
        body.put("vjudgeHandle", "usuario_vjudge");
        return body;
    }

    private static Map<String, Object> coachBody(String specialty, int years) {
        Map<String, Object> body = new HashMap<>();
        body.put("especialidadPrincipal", specialty);
        body.put("organizacionClub", "Club de Programación Competitiva");
        body.put("aniosExperiencia", years);
        body.put("presentacion", "Entrenador de maratones ICPC.");
        return body;
    }

    private Optional<Map<String, Object>> practitionerRow(Integer userId) {
        return row("SELECT * FROM " + SCHEMA + ".practicante WHERE usuario_id = ?", userId);
    }

    private Optional<Map<String, Object>> coachRow(Integer userId) {
        return row("SELECT * FROM " + SCHEMA + ".coach WHERE usuario_id = ?", userId);
    }

    private long count(String table, Integer userId) {
        return scalar("SELECT COUNT(*) FROM " + SCHEMA + "." + table + " WHERE usuario_id = ?", userId);
    }

    private void assertRoleUnchanged(User user, String role) {
        assertThat(row("SELECT rol FROM " + SCHEMA + ".usuario WHERE id = ?", user.getId()).orElseThrow())
                .containsEntry("rol", role);
    }

    private void assertColumn(String table, String column, String type, Integer length, String nullable) {
        Map<String, Object> info = row("""
                SELECT data_type, character_maximum_length, is_nullable FROM information_schema.columns
                WHERE table_schema = ? AND table_name = ? AND column_name = ?""", SCHEMA, table, column)
                .orElseThrow(() -> new AssertionError("Falta la columna " + table + "." + column));
        assertThat(info).as("%s.%s", table, column).containsEntry("data_type", type)
                .containsEntry("character_maximum_length", length).containsEntry("is_nullable", nullable);
    }

    private long scalar(String sql, Object... params) {
        return ((Number) row(sql, params).orElseThrow().values().iterator().next()).longValue();
    }

    private Optional<Map<String, Object>> row(String sql, Object... params) {
        try (Connection connection = connect(); var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            try (var result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                Map<String, Object> values = new HashMap<>();
                for (int i = 1; i <= result.getMetaData().getColumnCount(); i++) {
                    values.put(result.getMetaData().getColumnLabel(i), result.getObject(i));
                }
                return Optional.of(values);
            }
        } catch (SQLException exception) {
            throw new AssertionError("No se pudo consultar la base de pruebas", exception);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CodeforcesStubConfiguration {
        /** tourist: confirmado con rating; newbie_ok: confirmado sin rating; cualquier otro: no confirmado. */
        @Bean
        @Primary
        CodeforcesClient codeforcesStub() {
            return handle -> switch (handle) {
                case "tourist" -> Optional.of(new CodeforcesUserInfo("tourist", 3800));
                case "newbie_ok" -> Optional.of(new CodeforcesUserInfo("newbie_ok", null));
                default -> Optional.empty();
            };
        }
    }
}

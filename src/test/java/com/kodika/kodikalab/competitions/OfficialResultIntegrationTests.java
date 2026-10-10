package com.kodika.kodikalab.competitions;

import com.jayway.jsonpath.JsonPath;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import com.kodika.kodikalab.support.Bearer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "OFFICIAL_RESULT_TEST_DB_URL", matches = ".+")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OfficialResultIntegrationTests {
    private static final String SCHEMA = "official_result_test_" + UUID.randomUUID().toString().replace("-", "");
    private static boolean schemaCreated;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    String passwordHash;
    Account coach;
    int teamId;
    int competitionId;
    String session;

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(System.getenv("OFFICIAL_RESULT_TEST_DB_URL"),
                System.getenv().getOrDefault("OFFICIAL_RESULT_TEST_DB_USER", "postgres"),
                System.getenv().getOrDefault("OFFICIAL_RESULT_TEST_DB_PASSWORD", ""));
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) throws SQLException {
        try (Connection connection = connect(); var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + SCHEMA);
            schemaCreated = true;
        }
        registry.add("spring.datasource.url", () -> System.getenv("OFFICIAL_RESULT_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("OFFICIAL_RESULT_TEST_DB_USER", "postgres"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("OFFICIAL_RESULT_TEST_DB_PASSWORD", ""));
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

    @BeforeEach
    void setUp() throws Exception {
        passwordHash = encoder.encode("Password123");
        coach = account("COACH");
        teamId = team();
        competitionId = competition(teamId, "FINALIZADA", 1);
        session = login(coach);
    }

    @Test
    void confirmedResultAppearsInHistoryWithoutCreatingIndividualResolutions() throws Exception {
        create(competitionId, "{\"finalPosition\":2,\"solvedProblems\":0,\"confirm\":true}", session, 201);
        mvc.perform(get(history(teamId)).contextPath("/api").with(Bearer.of(session)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].competitionId").value(competitionId))
                .andExpect(jsonPath("$[0].teamId").value(teamId))
                .andExpect(jsonPath("$[0].status").value("CONFIRMADO"))
                .andExpect(jsonPath("$[0].solvedProblems").value(0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM resolucion_problema", Integer.class)).isZero();
    }

    @Test
    void partialPendingIsPrivateToCoachUntilCompletedAndConfirmed() throws Exception {
        create(competitionId, "{\"solvedProblems\":2}", session, 201);
        mvc.perform(get(history(teamId)).contextPath("/api").with(Bearer.of(session)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get(path(competitionId)).contextPath("/api").with(Bearer.of(session)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDIENTE"))
                .andExpect(jsonPath("$.finalPosition").isEmpty()).andExpect(jsonPath("$.confirmedAt").isEmpty());
        mvc.perform(put(path(competitionId)).contextPath("/api").with(Bearer.of(session))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirm\":true}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.finalPosition").exists())
                .andExpect(jsonPath("$.errors.solvedProblems").exists());
        assertThat(jdbc.queryForObject("SELECT problemas_resueltos FROM resultado_oficial_competencia WHERE competencia_id = ?",
                Integer.class, competitionId)).isEqualTo(2);
        mvc.perform(put(path(competitionId)).contextPath("/api").with(Bearer.of(session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"finalPosition\":4,\"solvedProblems\":3,\"confirm\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMADO"))
                .andExpect(jsonPath("$.confirmedAt").exists());
        mvc.perform(get(history(teamId)).contextPath("/api").with(Bearer.of(session)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        assertThat(countResults()).isEqualTo(1);
    }

    @Test
    void duplicateAndUpdateCannotOverwriteConfirmedInformation() throws Exception {
        create(competitionId, "{\"finalPosition\":1,\"solvedProblems\":5,\"confirm\":true}", session, 201);
        create(competitionId, "{\"finalPosition\":9,\"solvedProblems\":1,\"confirm\":true}", session, 409);
        mvc.perform(put(path(competitionId)).contextPath("/api").with(Bearer.of(session))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"finalPosition\":9}"))
                .andExpect(status().isConflict());
        mvc.perform(get(path(competitionId)).contextPath("/api").with(Bearer.of(session)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.finalPosition").value(1))
                .andExpect(jsonPath("$.solvedProblems").value(5)).andExpect(jsonPath("$.status").value("CONFIRMADO"));
        assertThat(countResults()).isEqualTo(1);
    }

    @Test
    void invalidCreationDoesNotPersistAndCannotConfirmUnfinishedCompetition() throws Exception {
        create(competitionId, "{\"finalPosition\":0,\"solvedProblems\":-1}", session, 400);
        create(competitionId, "{\"confirm\":true}", session, 400);
        assertThat(countResults()).isZero();
        jdbc.update("UPDATE competencia SET estado = 'EN_CURSO' WHERE id = ?", competitionId);
        create(competitionId, "{\"finalPosition\":1,\"solvedProblems\":2,\"confirm\":true}", session, 400);
        create(competitionId, "{}", session, 201);
        mvc.perform(put(path(competitionId)).contextPath("/api").with(Bearer.of(session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"finalPosition\":1,\"solvedProblems\":2,\"confirm\":true}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors['competition.status']").exists());
    }

    @Test
    void historyIsOrderedByCompetitionEndAndSeparatedByTeam() throws Exception {
        int older = competition(teamId, "FINALIZADA", 5);
        int otherTeam = team();
        int otherCompetition = competition(otherTeam, "FINALIZADA", 0);
        int pending = competition(teamId, "FINALIZADA", 0);
        for (int id : new int[]{competitionId, older, otherCompetition}) {
            create(id, "{\"finalPosition\":2,\"solvedProblems\":3,\"confirm\":true}", session, 201);
        }
        create(pending, "{\"finalPosition\":1,\"solvedProblems\":5}", session, 201);
        mvc.perform(get(history(teamId)).contextPath("/api").with(Bearer.of(session)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].competitionId").value(competitionId))
                .andExpect(jsonPath("$[1].competitionId").value(older));
        mvc.perform(get(history(otherTeam)).contextPath("/api").with(Bearer.of(session)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].competitionId").value(otherCompetition));
    }

    @ParameterizedTest
    @ValueSource(strings = {"COACH", "PRACTICANTE"})
    void unrelatedCoachAndPractitionerCannotReadOrWriteResults(String role) throws Exception {
        String other = login(account(role));
        create(competitionId, "{}", session, 201);
        create(competitionId, "{}", other, 403);
        mvc.perform(put(path(competitionId)).contextPath("/api").with(Bearer.of(other))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"finalPosition\":1}"))
                .andExpect(status().isForbidden());
        mvc.perform(get(path(competitionId)).contextPath("/api").with(Bearer.of(other)).param("userId", "" + coach.id()))
                .andExpect(status().isForbidden());
        mvc.perform(get(history(teamId)).contextPath("/api").with(Bearer.of(other)))
                .andExpect(status().isForbidden());
    }

    @Test
    void absentSessionOrSuspendedCoachCannotPublish() throws Exception {
        mvc.perform(post(path(competitionId)).contextPath("/api").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(history(teamId)).contextPath("/api")).andExpect(status().isUnauthorized());
        jdbc.update("UPDATE usuario SET estado_cuenta = 'SUSPENDIDO' WHERE id = ?", coach.id());
        create(competitionId, "{}", session, 403);
        mvc.perform(get(history(teamId)).contextPath("/api").with(Bearer.of(session))).andExpect(status().isForbidden());
        assertThat(countResults()).isZero();
    }

    @Test
    void missingCompetitionTeamAndResultReturn404() throws Exception {
        create(Integer.MAX_VALUE, "{}", session, 404);
        mvc.perform(get(history(Integer.MAX_VALUE)).contextPath("/api").with(Bearer.of(session)))
                .andExpect(status().isNotFound());
        mvc.perform(get(path(competitionId)).contextPath("/api").with(Bearer.of(session))).andExpect(status().isNotFound());
        mvc.perform(put(path(competitionId)).contextPath("/api").with(Bearer.of(session))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void databaseRejectsDuplicatesIncompleteConfirmationAndNegativeValues() throws Exception {
        create(competitionId, "{}", session, 201);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO resultado_oficial_competencia(competencia_id, estado, registrado_en)
                VALUES (?, 'PENDIENTE', CURRENT_TIMESTAMP)
                """, competitionId)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                UPDATE resultado_oficial_competencia SET estado = 'CONFIRMADO', confirmado_en = CURRENT_TIMESTAMP
                WHERE competencia_id = ?
                """, competitionId)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                UPDATE resultado_oficial_competencia SET posicion_final = -1 WHERE competencia_id = ?
                """, competitionId)).isInstanceOf(DataIntegrityViolationException.class);
        mvc.perform(get(path(competitionId)).contextPath("/api").with(Bearer.of(session)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDIENTE"));
    }

    @Test
    void concurrentCreationKeepsOneResult() throws Exception {
        List<Integer> statuses = concurrently(() -> mvc.perform(post(path(competitionId)).contextPath("/api")
                        .with(Bearer.of(session)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse().getStatus());
        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(countResults()).isEqualTo(1);
    }

    @Test
    void concurrentConfirmationCannotOverwriteConfirmedResult() throws Exception {
        create(competitionId, "{}", session, 201);
        List<Integer> statuses = concurrently(() -> mvc.perform(put(path(competitionId)).contextPath("/api")
                        .with(Bearer.of(session)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"finalPosition\":1,\"solvedProblems\":3,\"confirm\":true}"))
                .andReturn().getResponse().getStatus());
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(countResults()).isEqualTo(1);
    }

    private List<Integer> concurrently(Callable<Integer> request) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> gated = () -> {
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("No se pudo iniciar la solicitud concurrente");
                }
                return request.call();
            };
            var first = executor.submit(gated);
            var second = executor.submit(gated);
            start.countDown();
            return List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
        }
    }

    @Test
    void coachCreatesACompetitionAndThenRegistersAndConfirmsItsOfficialResult() throws Exception {
        String body = postCompetition(competitionBody(teamId, "ICPC Regional", "FINALIZADA", null), session)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.teamId").value(teamId))
                .andExpect(jsonPath("$.eventName").value("ICPC Regional"))
                .andExpect(jsonPath("$.accessType").value("PUBLICO_GRUPO"))
                .andExpect(jsonPath("$.penaltyRule").value("ICPC_20_MIN"))
                .andExpect(jsonPath("$.durationMinutes").value(300))
                .andExpect(jsonPath("$.scoreboardFreezeMinutes").value(60))
                .andExpect(jsonPath("$.status").value("FINALIZADA"))
                .andExpect(jsonPath("$.accessKey").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        int createdId = JsonPath.read(body, "$.id");

        create(createdId, "{\"finalPosition\":4,\"solvedProblems\":6,\"confirm\":true}", session, 201);
        mvc.perform(get(history(teamId)).contextPath("/api").with(Bearer.of(session)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].competitionId").value(createdId))
                .andExpect(jsonPath("$[0].eventName").value("ICPC Regional"))
                .andExpect(jsonPath("$[0].status").value("CONFIRMADO"));
    }

    @Test
    void newCompetitionDefaultsToScheduledAndCannotBeConfirmedUntilFinished() throws Exception {
        String body = postCompetition(competitionBody(teamId, "Simulacro futuro", null, null), session)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PROGRAMADA"))
                .andReturn().getResponse().getContentAsString();
        int createdId = JsonPath.read(body, "$.id");

        create(createdId, "{\"finalPosition\":1,\"solvedProblems\":2,\"confirm\":true}", session, 400);
        create(createdId, "{\"confirm\":false}", session, 201);
    }

    @Test
    void privateCompetitionStoresOnlyAHashOfTheAccessKey() throws Exception {
        String body = postCompetition(competitionBody(teamId, "Privada", null,
                "\"accessType\":\"PRIVADO_PASS\",\"accessKey\":\"secreto-123\""), session)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.accessType").value("PRIVADO_PASS"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secreto"))))
                .andReturn().getResponse().getContentAsString();
        int createdId = JsonPath.read(body, "$.id");

        String stored = jdbc.queryForObject("SELECT clave_acceso FROM competencia WHERE id = ?", String.class,
                createdId);
        assertThat(stored).startsWith("$2").doesNotContain("secreto");
        assertThat(encoder.matches("secreto-123", stored)).isTrue();
    }

    @Test
    void onlyTheTeamsActiveCoachCanCreateCompetitions() throws Exception {
        String valid = competitionBody(teamId, "No debe existir", null, null);
        postCompetition(valid, null).andExpect(status().isUnauthorized());
        postCompetition(valid, login(account("COACH"))).andExpect(status().isForbidden());
        postCompetition(valid, login(account("PRACTICANTE"))).andExpect(status().isForbidden());
        postCompetition(competitionBody(Integer.MAX_VALUE, "No debe existir", null, null), session)
                .andExpect(status().isNotFound());
        jdbc.update("UPDATE usuario SET estado_cuenta = 'SUSPENDIDO' WHERE id = ?", coach.id());
        postCompetition(valid, session).andExpect(status().isForbidden());

        assertThat(countCompetitionsNamed("No debe existir")).isZero();
    }

    @Test
    void invalidOrDuplicateCompetitionsAreNotStored() throws Exception {
        postCompetition("{\"teamId\":" + teamId + ",\"eventName\":\"  \","
                + "\"startsAt\":\"2026-10-01T14:00:00-05:00\",\"endsAt\":\"2026-10-01T13:00:00-05:00\"}", session)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.eventName").exists())
                .andExpect(jsonPath("$.errors.endsAt").exists());
        postCompetition("{\"teamId\":\"" + teamId + "\"}", session).andExpect(status().isBadRequest());
        assertThat(countCompetitionsNamed("Repetida")).isZero();

        postCompetition(competitionBody(teamId, "Repetida", null, null), session).andExpect(status().isCreated());
        postCompetition(competitionBody(teamId, "repetida", null, null), session).andExpect(status().isConflict());
        assertThat(countCompetitionsNamed("Repetida")).isEqualTo(1);
    }

    private org.springframework.test.web.servlet.ResultActions postCompetition(String body, String requester)
            throws Exception {
        var request = post("/api/competitions").contextPath("/api").contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (requester != null) {
            request.with(Bearer.of(requester));
        }
        return mvc.perform(request);
    }

    private static String competitionBody(int forTeam, String name, String state, String extra) {
        return "{\"teamId\":" + forTeam + ",\"eventName\":\"" + name + "\","
                + "\"startsAt\":\"2026-10-01T09:00:00-05:00\",\"endsAt\":\"2026-10-01T14:00:00-05:00\""
                + (state == null ? "" : ",\"status\":\"" + state + "\"")
                + (extra == null ? "" : "," + extra) + "}";
    }

    private int countCompetitionsNamed(String name) {
        return jdbc.queryForObject("SELECT count(*) FROM competencia WHERE lower(nombre_evento) = lower(?)",
                Integer.class, name);
    }

    private int countResults() {
        return jdbc.queryForObject("SELECT count(*) FROM resultado_oficial_competencia WHERE competencia_id = ?",
                Integer.class, competitionId);
    }

    private void create(int id, String body, String requester, int statusCode) throws Exception {
        mvc.perform(post(path(id)).contextPath("/api").with(Bearer.of(requester))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(statusCode));
    }

    private Account account(String role) {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String email = "test." + suffix + "@gmail.com";
        int id = jdbc.queryForObject("""
                INSERT INTO usuario(nombre_completo, correo, password_hash, rol, estado_cuenta)
                VALUES ('Usuario Prueba', ?, ?, ?, 'ACTIVO') RETURNING id
                """, Integer.class, email, passwordHash, role);
        if (role.equals("COACH")) {
            jdbc.update("INSERT INTO coach(usuario_id, especialidad_principal) VALUES (?, 'Grafos')", id);
        } else {
            jdbc.update("INSERT INTO practicante(usuario_id, codigo_estudiante, carrera) VALUES (?, ?, 'Ingeniería')",
                    id, suffix.substring(0, 20));
        }
        return new Account(id, email);
    }

    private int team() {
        return jdbc.queryForObject("""
                INSERT INTO grupo_estudio(coach_id, nombre, nivel_esperado, codigo_invitacion)
                VALUES (?, 'Equipo Prueba', 'Div3', ?) RETURNING id
                """, Integer.class, coach.id(), UUID.randomUUID().toString().substring(0, 20));
    }

    private int competition(int groupId, String state, int daysAgo) {
        return jdbc.queryForObject("""
                INSERT INTO competencia(grupo_id, nombre_evento, fecha_inicio, fecha_fin, estado)
                VALUES (?, 'Competencia Prueba', CURRENT_TIMESTAMP - (? * INTERVAL '1 day') - INTERVAL '6 hours',
                    CURRENT_TIMESTAMP - (? * INTERVAL '1 day') - INTERVAL '1 hour', ?) RETURNING id
                """, Integer.class, groupId, daysAgo, daysAgo, state);
    }

    private String login(Account account) throws Exception {
        var result = mvc.perform(post("/api/auth/login").contextPath("/api").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + account.email() + "\",\"password\":\"Password123\"}"))
                .andExpect(status().isOk()).andReturn();
        String token = Bearer.tokenFrom(result.getResponse().getContentAsString());
        assertThat(token).isNotBlank();
        return token;
    }

    private String path(int id) {
        return "/api/competitions/" + id + "/official-result";
    }

    private String history(int groupId) {
        return "/api/competitions/teams/" + groupId + "/official-results";
    }

    private record Account(int id, String email) {
    }
}

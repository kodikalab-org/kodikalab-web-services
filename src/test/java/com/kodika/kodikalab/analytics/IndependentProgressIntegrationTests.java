package com.kodika.kodikalab.analytics;

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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
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
@EnabledIfEnvironmentVariable(named = "INDEPENDENT_PROGRESS_TEST_DB_URL", matches = ".+")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class IndependentProgressIntegrationTests {
    private static final String SCHEMA = "independent_progress_test_" + UUID.randomUUID().toString().replace("-", "");
    private static boolean schemaCreated;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    String passwordHash;
    Account coach;
    Account practitioner;
    MockHttpSession practitionerSession;
    MockHttpSession coachSession;
    int firstTeam;
    int secondTeam;
    int firstMembership;
    int secondMembership;
    int sharedProblem;
    int firstAssigned;
    int secondAssigned;
    int topicId;

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(System.getenv("INDEPENDENT_PROGRESS_TEST_DB_URL"),
                System.getenv().getOrDefault("INDEPENDENT_PROGRESS_TEST_DB_USER", "postgres"),
                System.getenv().getOrDefault("INDEPENDENT_PROGRESS_TEST_DB_PASSWORD", ""));
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) throws SQLException {
        try (Connection connection = connect(); var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + SCHEMA);
            schemaCreated = true;
        }
        registry.add("spring.datasource.url", () -> System.getenv("INDEPENDENT_PROGRESS_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("INDEPENDENT_PROGRESS_TEST_DB_USER", "postgres"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("INDEPENDENT_PROGRESS_TEST_DB_PASSWORD", ""));
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
        practitioner = account("PRACTICANTE");
        firstTeam = team();
        secondTeam = team();
        firstMembership = membership(firstTeam, practitioner.id());
        secondMembership = membership(secondTeam, practitioner.id());
        topicId = jdbc.queryForObject("INSERT INTO tema(nombre) VALUES (?) RETURNING id", Integer.class,
                "Tema Prueba " + UUID.randomUUID());
        sharedProblem = problem();
        firstAssigned = assign(firstTeam, sharedProblem);
        secondAssigned = assign(secondTeam, sharedProblem);
        practitionerSession = login(practitioner);
        coachSession = login(coach);
    }

    @Test
    void registeringInOneTeamChangesOnlyItsProgressAndIgnoresSuppliedIdentity() throws Exception {
        assertProgress(firstTeam, firstMembership, 0);
        assertProgress(secondTeam, secondMembership, 0);
        standings(secondTeam);
        var secondSnapshot = snapshot(secondTeam);
        Account other = account("PRACTICANTE");
        int otherMembership = membership(firstTeam, other.id());
        mvc.perform(post(registerPath(firstTeam, firstAssigned)).contextPath("/api").session(practitionerSession)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"language":"Java 21","userId":%d,"membershipId":%d,"teamId":%d}
                                """.formatted(other.id(), otherMembership, secondTeam)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.registrationMethod").value("MANUAL_PROVISIONAL"))
                .andExpect(jsonPath("$.resolution.membershipId").value(firstMembership))
                .andExpect(jsonPath("$.resolution.membershipTeamId").value(firstTeam))
                .andExpect(jsonPath("$.resolution.competitionTeamId").value(firstTeam))
                .andExpect(jsonPath("$.progress.acceptedProblems").value(1));
        assertProgress(firstTeam, firstMembership, 1);
        assertProgress(secondTeam, secondMembership, 0);
        assertThat(count(firstMembership)).isEqualTo(1);
        assertThat(count(secondMembership)).isZero();
        assertThat(count(otherMembership)).isZero();
        assertThat(snapshot(secondTeam)).isEqualTo(secondSnapshot);
    }

    @Test
    void sameProblemCanBeResolvedIndependentlyInBothTeams() throws Exception {
        register(firstTeam, firstAssigned, practitionerSession, 201);
        register(secondTeam, secondAssigned, practitionerSession, 201);
        register(firstTeam, firstAssigned, practitionerSession, 409);
        assertProgress(firstTeam, firstMembership, 1);
        assertProgress(secondTeam, secondMembership, 1);
        assertThat(count(firstMembership)).isEqualTo(1);
        assertThat(count(secondMembership)).isEqualTo(1);
    }

    @Test
    void previousPendingAndRejectedAttemptsRemainAndSameProblemInAnotherCompetitionCountsOnce() throws Exception {
        attempt(firstMembership, firstAssigned, "PENDIENTE");
        attempt(firstMembership, firstAssigned, "WRONG_ANSWER");
        register(firstTeam, firstAssigned, practitionerSession, 201);
        register(firstTeam, assign(firstTeam, sharedProblem), practitionerSession, 201);
        register(firstTeam, firstAssigned, practitionerSession, 409);
        assertProgress(firstTeam, firstMembership, 1);
        assertThat(count(firstMembership)).isEqualTo(4);
        assertThat(jdbc.queryForList("SELECT veredicto FROM resolucion_problema WHERE practicante_grupo_id = ?",
                String.class, firstMembership)).containsExactlyInAnyOrder("PENDIENTE", "WRONG_ANSWER", "ACCEPTED", "ACCEPTED");
    }

    @Test
    void invalidContextCrossTeamAssignmentAndMissingMembershipDoNotWrite() throws Exception {
        int unrelatedTeam = team();
        register(firstTeam, secondAssigned, practitionerSession, 400);
        register(unrelatedTeam, assign(unrelatedTeam, sharedProblem), practitionerSession, 403);
        register(0, firstAssigned, practitionerSession, 400);
        register(Integer.MAX_VALUE, firstAssigned, practitionerSession, 404);
        register(firstTeam, Integer.MAX_VALUE, practitionerSession, 404);
        assertThat(count(firstMembership)).isZero();
        assertThat(count(secondMembership)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"RETIRADO", "EXPULSADO"})
    void revokedMembershipCannotWriteOrQueryPersonalProgress(String status) throws Exception {
        jdbc.update("UPDATE practicante_grupo SET estado = ? WHERE id = ?", status, firstMembership);
        register(firstTeam, firstAssigned, practitionerSession, 403);
        mvc.perform(get(progressPath(firstTeam)).contextPath("/api").session(practitionerSession))
                .andExpect(status().isForbidden());
        assertThat(count(firstMembership)).isZero();
        register(secondTeam, secondAssigned, practitionerSession, 201);
    }

    @Test
    void anonymousCoachSuspendedAndUnrelatedPractitionerCannotRegister() throws Exception {
        mvc.perform(post(registerPath(firstTeam, firstAssigned)).contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"language\":\"Java 21\"}"))
                .andExpect(status().isUnauthorized());
        register(firstTeam, firstAssigned, coachSession, 403);
        mvc.perform(get(progressPath(firstTeam)).contextPath("/api").session(coachSession))
                .andExpect(status().isForbidden());
        register(firstTeam, firstAssigned, login(account("PRACTICANTE")), 403);
        jdbc.update("UPDATE usuario SET estado_cuenta = 'SUSPENDIDO' WHERE id = ?", practitioner.id());
        register(firstTeam, firstAssigned, practitionerSession, 403);
        assertThat(count(firstMembership)).isZero();
    }

    @Test
    void invalidRequestFieldsDoNotChangeProgress() throws Exception {
        for (String body : new String[]{"{}", "{\"language\":21}", "{\"language\":\" \"}",
                "{\"language\":\"Java 21\",\"evidenceUrl\":\"not a URL\"}"}) {
            mvc.perform(post(registerPath(firstTeam, firstAssigned)).contextPath("/api").session(practitionerSession)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertProgress(firstTeam, firstMembership, 0);
        assertThat(count(firstMembership)).isZero();
    }

    @Test
    void calculationFailureRollsBackInsertedResolutionAndKeepsLastRankings() throws Exception {
        attempt(firstMembership, firstAssigned, "ACCEPTED");
        standings(firstTeam);
        standings(secondTeam);
        var firstSnapshot = snapshot(firstTeam);
        var secondSnapshot = snapshot(secondTeam);
        int affectedAssignment = assign(firstTeam, problem());
        int inconsistent = attempt(firstMembership, secondAssigned, "WRONG_ANSWER");
        int before = count(firstMembership);
        mvc.perform(post(registerPath(firstTeam, affectedAssignment)).contextPath("/api").session(practitionerSession)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"language\":\"Java 21\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.resolution").doesNotExist());
        assertThat(count(firstMembership)).isEqualTo(before);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM resolucion_problema
                WHERE practicante_grupo_id = ? AND competencia_problema_id = ?
                """, Integer.class, firstMembership, affectedAssignment)).isZero();
        assertThat(snapshot(firstTeam)).isEqualTo(firstSnapshot);
        assertThat(snapshot(secondTeam)).isEqualTo(secondSnapshot);
        jdbc.update("DELETE FROM resolucion_problema WHERE id = ?", inconsistent);
        register(firstTeam, affectedAssignment, practitionerSession, 201);
        assertProgress(firstTeam, firstMembership, 2);
    }

    @Test
    void concurrentDuplicateRegistrationKeepsOnlyOneAcceptedAttempt() throws Exception {
        Callable<Integer> request = () -> mvc.perform(post(registerPath(firstTeam, firstAssigned)).contextPath("/api")
                        .session(practitionerSession).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"Java 21\"}"))
                .andReturn().getResponse().getStatus();
        assertThat(concurrently(request, request)).containsExactlyInAnyOrder(201, 409);
        assertThat(count(firstMembership)).isEqualTo(1);
        assertProgress(firstTeam, firstMembership, 1);
    }

    @Test
    void concurrentRegistrationsInDifferentTeamsRemainIndependent() throws Exception {
        Callable<Integer> first = () -> mvc.perform(post(registerPath(firstTeam, firstAssigned)).contextPath("/api")
                        .session(practitionerSession).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"Java 21\"}"))
                .andReturn().getResponse().getStatus();
        Callable<Integer> second = () -> mvc.perform(post(registerPath(secondTeam, secondAssigned)).contextPath("/api")
                        .session(practitionerSession).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"Java 21\"}"))
                .andReturn().getResponse().getStatus();
        assertThat(concurrently(first, second)).containsExactly(201, 201);
        assertProgress(firstTeam, firstMembership, 1);
        assertProgress(secondTeam, secondMembership, 1);
    }

    @Test
    void rankingTopicsAndOfficialHistoryKeepTheirExistingTeamScope() throws Exception {
        attempt(firstMembership, firstAssigned, "ACCEPTED");
        attempt(secondMembership, secondAssigned, "ACCEPTED");
        int addedAssignment = assign(firstTeam, problem());
        confirmedOfficialResult(firstAssigned);
        confirmedOfficialResult(secondAssigned);
        String firstHistory = officialHistory(firstTeam);
        String secondHistory = officialHistory(secondTeam);
        String otherTopics = topics(secondTeam);
        standings(secondTeam);
        var otherSnapshot = snapshot(secondTeam);
        register(firstTeam, addedAssignment, practitionerSession, 201);
        mvc.perform(get("/api/analytics/teams/" + firstTeam + "/standings").contextPath("/api").session(coachSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.members[0].acceptedProblems").value(2));
        mvc.perform(get("/api/analytics/teams/" + firstTeam + "/weaknesses").contextPath("/api").session(coachSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.topics[0].solvedProblems").value(2))
                .andExpect(jsonPath("$.topics[0].coveragePercentage").value(100.0));
        assertThat(topics(secondTeam)).isEqualTo(otherTopics);
        assertThat(snapshot(secondTeam)).isEqualTo(otherSnapshot);
        assertThat(officialHistory(firstTeam)).isEqualTo(firstHistory);
        assertThat(officialHistory(secondTeam)).isEqualTo(secondHistory);
        assertProgress(secondTeam, secondMembership, 1);
    }

    private List<Integer> concurrently(Callable<Integer> first, Callable<Integer> second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstResult = executor.submit(() -> { start.await(10, TimeUnit.SECONDS); return first.call(); });
            var secondResult = executor.submit(() -> { start.await(10, TimeUnit.SECONDS); return second.call(); });
            start.countDown();
            return List.of(firstResult.get(30, TimeUnit.SECONDS), secondResult.get(30, TimeUnit.SECONDS));
        }
    }

    private void assertProgress(int groupId, int memberId, int accepted) throws Exception {
        mvc.perform(get(progressPath(groupId)).contextPath("/api").session(practitionerSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.teamId").value(groupId))
                .andExpect(jsonPath("$.membershipId").value(memberId))
                .andExpect(jsonPath("$.userId").value(practitioner.id()))
                .andExpect(jsonPath("$.acceptedProblems").value(accepted));
    }

    private void register(int groupId, int assignedId, MockHttpSession session, int statusCode) throws Exception {
        mvc.perform(post(registerPath(groupId, assignedId)).contextPath("/api").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"language\":\"Java 21\"}"))
                .andExpect(status().is(statusCode));
    }

    private void standings(int groupId) throws Exception {
        mvc.perform(get("/api/analytics/teams/" + groupId + "/standings").contextPath("/api").session(coachSession))
                .andExpect(status().isOk());
    }

    private java.util.Map<String, Object> snapshot(int groupId) {
        return jdbc.queryForMap("SELECT calculado_en, resultado::text FROM ranking_equipo_actual WHERE grupo_id = ?", groupId);
    }

    private String topics(int groupId) throws Exception {
        return mvc.perform(get("/api/analytics/teams/" + groupId + "/weaknesses").contextPath("/api").session(coachSession))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private void confirmedOfficialResult(int assignedId) throws Exception {
        int competition = jdbc.queryForObject("SELECT competencia_id FROM competencia_problema WHERE id = ?", Integer.class, assignedId);
        mvc.perform(post("/api/competitions/" + competition + "/official-result").contextPath("/api").session(coachSession)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"finalPosition\":1,\"solvedProblems\":1,\"confirm\":true}"))
                .andExpect(status().isCreated());
    }

    private String officialHistory(int groupId) throws Exception {
        return mvc.perform(get("/api/competitions/teams/" + groupId + "/official-results").contextPath("/api").session(coachSession))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private int count(int memberId) {
        return jdbc.queryForObject("SELECT count(*) FROM resolucion_problema WHERE practicante_grupo_id = ?", Integer.class, memberId);
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

    private int membership(int groupId, int userId) {
        return jdbc.queryForObject("INSERT INTO practicante_grupo(grupo_id, practicante_id) VALUES (?, ?) RETURNING id",
                Integer.class, groupId, userId);
    }

    private int problem() {
        int id = jdbc.queryForObject("""
                INSERT INTO problema(titulo, url_problema) VALUES ('Problema Prueba', 'https://example.com/problem') RETURNING id
                """, Integer.class);
        jdbc.update("INSERT INTO problema_tema(problema_id, tema_id) VALUES (?, ?)", id, topicId);
        return id;
    }

    private int assign(int groupId, int problemId) {
        int competition = jdbc.queryForObject("""
                INSERT INTO competencia(grupo_id, nombre_evento, fecha_inicio, fecha_fin, estado)
                VALUES (?, 'Competencia Prueba', CURRENT_TIMESTAMP - INTERVAL '6 hours',
                    CURRENT_TIMESTAMP - INTERVAL '1 hour', 'FINALIZADA') RETURNING id
                """, Integer.class, groupId);
        return jdbc.queryForObject("""
                INSERT INTO competencia_problema(competencia_id, problema_id, orden_letra) VALUES (?, ?, 'A') RETURNING id
                """, Integer.class, competition, problemId);
    }

    private int attempt(int memberId, int assignedId, String verdict) {
        return jdbc.queryForObject("""
                INSERT INTO resolucion_problema(practicante_grupo_id, competencia_problema_id, veredicto)
                VALUES (?, ?, ?) RETURNING id
                """, Integer.class, memberId, assignedId, verdict);
    }

    private MockHttpSession login(Account account) throws Exception {
        var result = mvc.perform(post("/api/auth/login").contextPath("/api").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + account.email() + "\",\"password\":\"Password123\"}"))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }

    private String progressPath(int groupId) {
        return "/api/analytics/teams/" + groupId + "/progress/me";
    }

    private String registerPath(int groupId, int assignedId) {
        return "/api/competitions/teams/" + groupId + "/problems/" + assignedId + "/resolutions";
    }

    private record Account(int id, String email) {
    }
}

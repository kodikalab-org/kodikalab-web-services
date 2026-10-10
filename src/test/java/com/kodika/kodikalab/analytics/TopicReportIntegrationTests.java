package com.kodika.kodikalab.analytics;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
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
import com.kodika.kodikalab.support.Bearer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TOPIC_REPORT_TEST_DB_URL", matches = ".+")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TopicReportIntegrationTests {
    private static final String SCHEMA = "topic_report_test_" + UUID.randomUUID().toString().replace("-", "");
    private static boolean schemaCreated;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    String passwordHash;
    Account coach;
    Account practitioner;
    int teamId;
    int membershipId;
    int firstProblem;
    int secondProblem;
    int thirdProblem;
    int firstAssigned;
    int secondAssigned;
    int thirdAssigned;
    int graphTopic;
    int treeTopic;

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(System.getenv("TOPIC_REPORT_TEST_DB_URL"),
                System.getenv().getOrDefault("TOPIC_REPORT_TEST_DB_USER", "postgres"),
                System.getenv().getOrDefault("TOPIC_REPORT_TEST_DB_PASSWORD", ""));
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) throws SQLException {
        try (Connection connection = connect(); var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + SCHEMA);
            schemaCreated = true;
        }
        registry.add("spring.datasource.url", () -> System.getenv("TOPIC_REPORT_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("TOPIC_REPORT_TEST_DB_USER", "postgres"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("TOPIC_REPORT_TEST_DB_PASSWORD", ""));
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
    void createData() {
        passwordHash = encoder.encode("Password123");
        coach = account("COACH");
        practitioner = account("PRACTICANTE");
        teamId = team();
        membershipId = membership(teamId);
        firstProblem = problem();
        secondProblem = problem();
        thirdProblem = problem();
        firstAssigned = assign(teamId, firstProblem, "FINALIZADA");
        secondAssigned = assign(teamId, secondProblem, "FINALIZADA");
        thirdAssigned = assign(teamId, thirdProblem, "FINALIZADA");
        graphTopic = topic();
        treeTopic = topic();
        classify(firstProblem, graphTopic);
        classify(secondProblem, graphTopic);
        classify(thirdProblem, treeTopic);
    }

    @Test
    void readsAssignedUniverseAndCountsPendingWithoutInvalidatingReport() throws Exception {
        resolution(membershipId, firstAssigned, "ACCEPTED");
        resolution(membershipId, assign(teamId, firstProblem, "FINALIZADA"), "ACCEPTED");
        resolution(membershipId, secondAssigned, "PENDIENTE");
        resolution(membershipId, thirdAssigned, "WRONG_ANSWER");
        resolution(membershipId, assign(teamId, problem(), "EN_CURSO"), "ACCEPTED");

        mvc.perform(get(path(teamId)).contextPath("/api").with(Bearer.of(login(coach))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingResolutions").value(1))
                .andExpect(jsonPath("$.topics.length()").value(2))
                .andExpect(jsonPath("$.topics[0].topicId").value(treeTopic))
                .andExpect(jsonPath("$.topics[0].solvedProblems").value(0))
                .andExpect(jsonPath("$.topics[0].lowestCoverage").value(true))
                .andExpect(jsonPath("$.topics[1].assignedProblems").value(2))
                .andExpect(jsonPath("$.topics[1].solvedProblems").value(1))
                .andExpect(jsonPath("$.topics[1].coveragePercentage").value(50.0));
    }

    @Test
    void multipleTopicsAndEqualCoverageKeepAllMinimumTopics() throws Exception {
        classify(firstProblem, treeTopic);
        resolution(membershipId, firstAssigned, "ACCEPTED");

        mvc.perform(get(path(teamId)).contextPath("/api").with(Bearer.of(login(coach))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topics[0].lowestCoverage").value(true))
                .andExpect(jsonPath("$.topics[1].lowestCoverage").value(true))
                .andExpect(jsonPath("$.topics[0].assignedProblems").value(2))
                .andExpect(jsonPath("$.topics[1].assignedProblems").value(2));
    }

    @Test
    void samePractitionerAndProblemInAnotherTeamDoesNotChangeCoverage() throws Exception {
        int otherTeam = team();
        int otherMembership = membership(otherTeam);
        resolution(membershipId, firstAssigned, "ACCEPTED");
        resolution(otherMembership, assign(otherTeam, secondProblem, "FINALIZADA"), "ACCEPTED");

        mvc.perform(get(path(teamId)).contextPath("/api").with(Bearer.of(login(coach))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topics[1].solvedProblems").value(1));
    }

    @Test
    void crossTeamResolutionIsRejectedFromBothTeams() throws Exception {
        int otherTeam = team();
        membership(otherTeam);
        resolution(membershipId, firstAssigned, "ACCEPTED");
        resolution(membershipId, assign(otherTeam, firstProblem, "FINALIZADA"), "ACCEPTED");
        String session = login(coach);

        for (int id : new int[]{teamId, otherTeam}) {
            mvc.perform(get(path(id)).contextPath("/api").with(Bearer.of(session)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errors").isNotEmpty())
                    .andExpect(jsonPath("$.topics").doesNotExist());
        }
    }

    @Test
    void missingTopicBlocksConclusionsAndCoachCanRetryAfterCorrection() throws Exception {
        resolution(membershipId, firstAssigned, "ACCEPTED");
        jdbc.update("DELETE FROM problema_tema WHERE problema_id = ?", secondProblem);
        String session = login(coach);
        mvc.perform(get(path(teamId)).contextPath("/api").with(Bearer.of(session)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.topics").exists())
                .andExpect(jsonPath("$.topics").doesNotExist());
        classify(secondProblem, graphTopic);
        mvc.perform(get(path(teamId)).contextPath("/api").with(Bearer.of(session))).andExpect(status().isOk());
    }

    @Test
    void noCompletedActivityOrOnlyPendingAttemptsDoesNotProduceConclusions() throws Exception {
        String session = login(coach);
        resolution(membershipId, firstAssigned, "PENDIENTE");
        mvc.perform(get(path(teamId)).contextPath("/api").with(Bearer.of(session)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.resolutions").exists());
        jdbc.update("UPDATE competencia SET estado = 'PROGRAMADA' WHERE grupo_id = ?", teamId);
        mvc.perform(get(path(teamId)).contextPath("/api").with(Bearer.of(session)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.assignments").exists());
    }

    @ParameterizedTest
    @ValueSource(strings = {"COACH", "PRACTICANTE"})
    void unrelatedCoachAndMemberCannotAccessReport(String role) throws Exception {
        Account requester = role.equals("COACH") ? account(role) : practitioner;
        mvc.perform(get(path(teamId)).contextPath("/api").with(Bearer.of(login(requester)))
                        .param("userId", String.valueOf(coach.id())))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousRequestIsUnauthorized() throws Exception {
        mvc.perform(get(path(teamId)).contextPath("/api")).andExpect(status().isUnauthorized());
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

    private int membership(int groupId) {
        return jdbc.queryForObject("INSERT INTO practicante_grupo(grupo_id, practicante_id) VALUES (?, ?) RETURNING id",
                Integer.class, groupId, practitioner.id());
    }

    private int problem() {
        return jdbc.queryForObject("""
                INSERT INTO problema(titulo, url_problema) VALUES ('Problema Prueba', 'https://example.com/problem')
                RETURNING id
                """, Integer.class);
    }

    private int topic() {
        return jdbc.queryForObject("INSERT INTO tema(nombre) VALUES (?) RETURNING id", Integer.class,
                "Tema Prueba " + UUID.randomUUID());
    }

    private void classify(int problemId, int topicId) {
        jdbc.update("INSERT INTO problema_tema(problema_id, tema_id) VALUES (?, ?)", problemId, topicId);
    }

    private int assign(int groupId, int problemId, String status) {
        int competition = jdbc.queryForObject("""
                INSERT INTO competencia(grupo_id, nombre_evento, fecha_inicio, fecha_fin, estado)
                VALUES (?, 'Competencia Prueba', CURRENT_TIMESTAMP - INTERVAL '6 hours',
                    CURRENT_TIMESTAMP - INTERVAL '1 hour', ?) RETURNING id
                """, Integer.class, groupId, status);
        return jdbc.queryForObject("""
                INSERT INTO competencia_problema(competencia_id, problema_id, orden_letra)
                VALUES (?, ?, 'A') RETURNING id
                """, Integer.class, competition, problemId);
    }

    private void resolution(int memberId, int assignedId, String verdict) {
        jdbc.update("""
                INSERT INTO resolucion_problema(practicante_grupo_id, competencia_problema_id, veredicto)
                VALUES (?, ?, ?)
                """, memberId, assignedId, verdict);
    }

    private String login(Account account) throws Exception {
        var result = mvc.perform(post("/api/auth/login").contextPath("/api").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + account.email() + "\",\"password\":\"Password123\"}"))
                .andExpect(status().isOk()).andReturn();
        String token = Bearer.tokenFrom(result.getResponse().getContentAsString());
        assertThat(token).isNotBlank();
        return token;
    }

    private String path(int groupId) {
        return "/api/analytics/teams/" + groupId + "/weaknesses";
    }

    private record Account(int id, String email) {
    }
}

package com.kodika.kodikalab.analytics;

import com.kodika.kodikalab.analytics.dto.TeamRankingResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.List;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "RANKING_TEST_DB_URL", matches = ".+")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RankingIntegrationTests {
    private static final String SCHEMA = "ranking_test_" + UUID.randomUUID().toString().replace("-", "");
    private static final String PASSWORD = "Password123";
    private static boolean schemaCreated;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper mapper;
    @Autowired TeamRankingSnapshotRepository snapshotRepository;
    @Autowired RankingSnapshotService snapshotService;
    String encodedPassword;
    Account coach;
    Account first;
    Account second;
    Account third;
    Account inactive;
    int teamId;
    int firstMembership;
    int secondMembership;
    int inactiveMembership;
    int problemId;
    int competitionProblemId;

    private static Connection connect() throws SQLException {
        String url = System.getenv("RANKING_TEST_DB_URL");
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("Configure RANKING_TEST_DB_URL hacia una base exclusiva de pruebas");
        }
        return DriverManager.getConnection(url,
                System.getenv().getOrDefault("RANKING_TEST_DB_USER", "postgres"),
                System.getenv().getOrDefault("RANKING_TEST_DB_PASSWORD", ""));
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) throws SQLException {
        try (Connection connection = connect(); var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + SCHEMA);
            schemaCreated = true;
        }
        registry.add("spring.datasource.url", () -> System.getenv("RANKING_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("RANKING_TEST_DB_USER", "postgres"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("RANKING_TEST_DB_PASSWORD", ""));
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
    void createTeamData() {
        encodedPassword = encoder.encode(PASSWORD);
        coach = account("COACH");
        first = account("PRACTICANTE");
        second = account("PRACTICANTE");
        third = account("PRACTICANTE");
        inactive = account("PRACTICANTE");
        teamId = team(coach.id());
        firstMembership = membership(teamId, first.id(), "ACTIVO");
        secondMembership = membership(teamId, second.id(), "ACTIVO");
        membership(teamId, third.id(), "ACTIVO");
        inactiveMembership = membership(teamId, inactive.id(), "RETIRADO");
        problemId = problem();
        competitionProblemId = assignedProblem(teamId, problemId);
    }

    @Test
    void readsPersistedRankingDeduplicatesAcrossCompetitionsAndIncludesZeros() throws Exception {
        resolution(firstMembership, competitionProblemId, "ACCEPTED");
        resolution(firstMembership, competitionProblemId, "ACCEPTED");
        resolution(firstMembership, assignedProblem(teamId, problemId), "ACCEPTED");
        resolution(firstMembership, assignedProblem(teamId, problem()), "ACCEPTED");
        resolution(secondMembership, competitionProblemId, "ACCEPTED");
        resolution(inactiveMembership, competitionProblemId, "ACCEPTED");

        mvc.perform(get(path(teamId)).contextPath("/api").session(login(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(3))
                .andExpect(jsonPath("$.members[0].userId").value(first.id()))
                .andExpect(jsonPath("$.members[0].acceptedProblems").value(2))
                .andExpect(jsonPath("$.members[1].acceptedProblems").value(1))
                .andExpect(jsonPath("$.members[2].acceptedProblems").value(0))
                .andExpect(jsonPath("$.members[2].position").value(3));
    }

    @Test
    void coachReadsSharedPositionsWithDeterministicOrder() throws Exception {
        resolution(firstMembership, competitionProblemId, "ACCEPTED");
        resolution(secondMembership, competitionProblemId, "ACCEPTED");

        mvc.perform(get(path(teamId)).contextPath("/api").session(login(coach)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[0].membershipId").value(firstMembership))
                .andExpect(jsonPath("$.members[0].position").value(1))
                .andExpect(jsonPath("$.members[1].position").value(1))
                .andExpect(jsonPath("$.members[2].position").value(3));
    }

    @Test
    void activityWithoutAcceptancesGivesSharedZeroScores() throws Exception {
        resolution(firstMembership, competitionProblemId, "WRONG_ANSWER");

        mvc.perform(get(path(teamId)).contextPath("/api").session(login(coach)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CALCULATED"))
                .andExpect(jsonPath("$.members[0].acceptedProblems").value(0))
                .andExpect(jsonPath("$.members[2].position").value(1));
    }

    @Test
    void assignedProblemWithoutResolutionsHasNoActivity() throws Exception {
        mvc.perform(get(path(teamId)).contextPath("/api").session(login(coach)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_ACTIVITY"))
                .andExpect(jsonPath("$.members").isEmpty());
    }

    @Test
    void sameUserAndProblemKeepIndependentTeamScores() throws Exception {
        int otherTeam = team(coach.id());
        int otherMembership = membership(otherTeam, first.id(), "ACTIVO");
        resolution(firstMembership, competitionProblemId, "ACCEPTED");
        resolution(otherMembership, assignedProblem(otherTeam, problemId), "WRONG_ANSWER");
        MockHttpSession session = login(first);

        mvc.perform(get(path(teamId)).contextPath("/api").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[0].acceptedProblems").value(1));
        mvc.perform(get(path(otherTeam)).contextPath("/api").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[0].acceptedProblems").value(0));
    }

    @Test
    void crossTeamResolutionIsDetectedFromBothSidesInsteadOfFilteredOut() throws Exception {
        int otherTeam = team(coach.id());
        resolution(firstMembership, competitionProblemId, "ACCEPTED");
        resolution(firstMembership, assignedProblem(otherTeam, problemId), "ACCEPTED");
        MockHttpSession session = login(coach);

        for (int id : new int[]{teamId, otherTeam}) {
            mvc.perform(get(path(id)).contextPath("/api").session(session))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errors").isNotEmpty())
                    .andExpect(jsonPath("$.members").doesNotExist());
        }
    }

    @Test
    void rowsWithStatesOutsideMembershipDoNotBreakRanking() throws Exception {
        // Simula estados que una historia futura (solicitudes de ingreso) agregue a practicante_grupo.estado:
        // no son membresías del ranking y no deben impedir calcularlo ni materializarse como enum.
        jdbc.execute("""
                DO $$ DECLARE c text; BEGIN
                    FOR c IN SELECT conname FROM pg_constraint
                             WHERE conrelid = 'practicante_grupo'::regclass AND contype = 'c'
                    LOOP EXECUTE 'ALTER TABLE practicante_grupo DROP CONSTRAINT ' || quote_ident(c); END LOOP;
                END $$
                """);
        membership(teamId, account("PRACTICANTE").id(), "PENDIENTE");
        membership(teamId, account("PRACTICANTE").id(), "RECHAZADO");
        resolution(firstMembership, competitionProblemId, "ACCEPTED");

        mvc.perform(get(path(teamId)).contextPath("/api").session(login(coach)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CALCULATED"))
                .andExpect(jsonPath("$.members.length()").value(3))
                .andExpect(jsonPath("$.members[0].userId").value(first.id()));
    }

    @Test
    void anonymousRequestIsUnauthorized() throws Exception {
        mvc.perform(get(path(teamId)).contextPath("/api"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errors").isMap());
    }

    @Test
    void inactiveMemberIsForbidden() throws Exception {
        mvc.perform(get(path(teamId)).contextPath("/api").session(login(inactive)))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PRACTICANTE", "COACH"})
    void unrelatedAccountIsForbiddenEvenWhenSupplyingMemberId(String role) throws Exception {
        mvc.perform(get(path(teamId)).contextPath("/api").session(login(account(role)))
                        .param("userId", String.valueOf(first.id())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.members").doesNotExist());
    }

    @Test
    void missingTeamIsNotFound() throws Exception {
        mvc.perform(get(path(Integer.MAX_VALUE)).contextPath("/api").session(login(coach)))
                .andExpect(status().isNotFound());
    }

    @Test
    void preservesPersistedRankingOnInconsistencyAndReplacesItAfterRecovery() throws Exception {
        resolution(firstMembership, competitionProblemId, "ACCEPTED");
        MockHttpSession session = login(coach);
        String valid = mvc.perform(get(path(teamId)).contextPath("/api").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String stored = snapshotRepository.findById(teamId).orElseThrow().getResult();
        assertThat(mapper.readTree(stored)).isEqualTo(mapper.readTree(valid));
        OffsetDateTime timestamp = snapshotRepository.findById(teamId).orElseThrow().getCalculatedAt();

        int otherTeam = team(coach.id());
        int foreignProblem = assignedProblem(otherTeam, problemId);
        resolution(firstMembership, foreignProblem, "ACCEPTED");
        String failed = mvc.perform(get(path(teamId)).contextPath("/api").session(session))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.members").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(failed).path("lastValidRanking").path("ranking"))
                .isEqualTo(mapper.readTree(valid));
        assertThat(snapshotRepository.findById(teamId).orElseThrow().getCalculatedAt()).isEqualTo(timestamp);

        jdbc.update("DELETE FROM resolucion_problema WHERE competencia_problema_id = ?", foreignProblem);
        resolution(firstMembership, assignedProblem(teamId, problem()), "ACCEPTED");
        mvc.perform(get(path(teamId)).contextPath("/api").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[0].acceptedProblems").value(2));
        var recovered = snapshotService.findByTeamId(teamId).orElseThrow();
        assertThat(recovered.ranking().members().getFirst().acceptedProblems()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ranking_equipo_actual WHERE grupo_id = ?",
                Integer.class, teamId)).isEqualTo(1);
    }

    @Test
    void oldRequestCannotOverwriteNewerStoredSnapshotAndTeamsRemainIndependent() {
        var newer = new TeamRankingResponse(teamId,
                TeamRankingResponse.Status.NO_ACTIVITY, "order", "tie", List.of());
        var older = new TeamRankingResponse(teamId,
                TeamRankingResponse.Status.CALCULATED, "order", "tie", List.of());
        OffsetDateTime time = OffsetDateTime.now();
        snapshotService.save(newer, time);
        snapshotService.save(older, time.minusSeconds(1));
        snapshotService.save(older, time);

        assertThat(snapshotService.findByTeamId(teamId).orElseThrow().ranking()).isEqualTo(newer);
        int otherTeam = team(coach.id());
        assertThat(snapshotService.findByTeamId(otherTeam)).isEmpty();
    }

    @Test
    void revokedMemberCannotReadPreviouslyStoredRanking() throws Exception {
        MockHttpSession session = login(first);
        mvc.perform(get(path(teamId)).contextPath("/api").session(session)).andExpect(status().isOk());
        jdbc.update("UPDATE practicante_grupo SET estado = 'RETIRADO' WHERE id = ?", firstMembership);

        mvc.perform(get(path(teamId)).contextPath("/api").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.lastValidRanking").doesNotExist());
        assertThat(snapshotRepository.findById(teamId)).isPresent();
    }

    private Account account(String role) {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String email = "test." + suffix + "@gmail.com";
        int id = jdbc.queryForObject("""
                INSERT INTO usuario(nombre_completo, correo, password_hash, rol, estado_cuenta)
                VALUES ('Usuario Prueba', ?, ?, ?, 'ACTIVO') RETURNING id
                """, Integer.class, email, encodedPassword, role);
        if ("COACH".equals(role)) {
            jdbc.update("INSERT INTO coach(usuario_id, especialidad_principal) VALUES (?, 'Grafos')", id);
        } else {
            jdbc.update("INSERT INTO practicante(usuario_id, codigo_estudiante, carrera) VALUES (?, ?, 'Ingeniería')",
                    id, suffix.substring(0, 20));
        }
        return new Account(id, email);
    }

    private int team(int coachId) {
        return jdbc.queryForObject("""
                INSERT INTO grupo_estudio(coach_id, nombre, nivel_esperado, codigo_invitacion)
                VALUES (?, 'Equipo Prueba', 'Div3', ?) RETURNING id
                """, Integer.class, coachId, UUID.randomUUID().toString().substring(0, 20));
    }

    private int membership(int groupId, int userId, String status) {
        return jdbc.queryForObject("""
                INSERT INTO practicante_grupo(grupo_id, practicante_id, estado)
                VALUES (?, ?, ?) RETURNING id
                """, Integer.class, groupId, userId, status);
    }

    private int problem() {
        return jdbc.queryForObject("""
                INSERT INTO problema(titulo, url_problema)
                VALUES ('Problema Prueba', 'https://example.com/problem') RETURNING id
                """, Integer.class);
    }

    private int assignedProblem(int groupId, int assignedProblemId) {
        int competitionId = jdbc.queryForObject("""
                INSERT INTO competencia(grupo_id, nombre_evento, fecha_inicio, fecha_fin)
                VALUES (?, 'Competencia Prueba', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '5 hours')
                RETURNING id
                """, Integer.class, groupId);
        return jdbc.queryForObject("""
                INSERT INTO competencia_problema(competencia_id, problema_id, orden_letra)
                VALUES (?, ?, 'A') RETURNING id
                """, Integer.class, competitionId, assignedProblemId);
    }

    private void resolution(int membershipId, int assignedProblemId, String verdict) {
        jdbc.update("""
                INSERT INTO resolucion_problema(practicante_grupo_id, competencia_problema_id, veredicto)
                VALUES (?, ?, ?)
                """, membershipId, assignedProblemId, verdict);
    }

    private MockHttpSession login(Account account) throws Exception {
        var result = mvc.perform(post("/api/auth/login").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + account.email() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }

    private String path(int id) {
        return "/api/analytics/teams/" + id + "/standings";
    }

    private record Account(int id, String email) {
    }
}

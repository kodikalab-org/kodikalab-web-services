package com.kodika.kodikalab.teams;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@EnabledIfEnvironmentVariable(named = "NAVIGATION_TEST_DB_URL", matches = ".+")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TeamNavigationIntegrationTests {
    private static final String SCHEMA = "navigation_test_" + UUID.randomUUID().toString().replace("-", "");
    private static final String PASSWORD = UUID.randomUUID() + "Aa9!";
    private static boolean created;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper mapper;
    Account coach;
    Account practitioner;
    Account outsider;
    int alpha;
    int beta;
    int membership;
    int problem;
    int alphaCompetition;
    int betaCompetition;

    private static Connection connection() throws SQLException {
        String url = System.getenv("NAVIGATION_TEST_DB_URL");
        if (!"jdbc:postgresql://127.0.0.1:5432/kodikalab_us11_14_test".equals(url)) {
            throw new IllegalStateException("Use exclusivamente kodikalab_us11_14_test");
        }
        Connection connection = DriverManager.getConnection(url, System.getenv("NAVIGATION_TEST_DB_USER"),
                System.getenv("NAVIGATION_TEST_DB_PASSWORD"));
        if (!"kodikalab_us11_14_test".equals(connection.getCatalog())) {
            connection.close();
            throw new IllegalStateException("Base de pruebas incorrecta");
        }
        return connection;
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry properties) throws SQLException {
        try (Connection connection = connection(); var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + SCHEMA);
            created = true;
        }
        properties.add("spring.datasource.url", () -> System.getenv("NAVIGATION_TEST_DB_URL"));
        properties.add("spring.datasource.username", () -> System.getenv("NAVIGATION_TEST_DB_USER"));
        properties.add("spring.datasource.password", () -> System.getenv("NAVIGATION_TEST_DB_PASSWORD"));
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        properties.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        properties.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA);
        properties.add("spring.jpa.show-sql", () -> "false");
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        if (created) {
            try (Connection connection = connection(); var statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA " + SCHEMA + " CASCADE");
            }
        }
    }

    @BeforeEach
    void prepare() {
        coach = account("COACH");
        practitioner = account("PRACTICANTE");
        outsider = account("COACH");
        alpha = team(coach.id());
        beta = team(outsider.id());
        membership = jdbc.queryForObject("INSERT INTO practicante_grupo(grupo_id,practicante_id) VALUES (?,?) RETURNING id",
                Integer.class, alpha, practitioner.id());
        problem = jdbc.queryForObject("INSERT INTO problema(titulo,url_problema) VALUES ('Problema compartido','https://example.test/problem') RETURNING id", Integer.class);
        alphaCompetition = competition(alpha);
        betaCompetition = competition(beta);
        assign(alphaCompetition);
        assign(betaCompetition);
    }

    @Test
    void myTeamsUsesSessionAndSeparatesCoachAndPractitionerContracts() throws Exception {
        mvc.perform(get("/api/teams/me").contextPath("/api").session(login(coach)).param("userId", "999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].groupId").value(alpha))
                .andExpect(jsonPath("$[0].activeMembers").value(1))
                .andExpect(jsonPath("$[0].invitationCode").exists())
                .andExpect(jsonPath("$[0].membership").doesNotExist())
                .andExpect(jsonPath("$[0].pendingRequests").doesNotExist());
        mvc.perform(get("/api/teams/me").contextPath("/api").session(login(practitioner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].membership.membershipId").value(membership))
                .andExpect(jsonPath("$[0].membership.status").value("ACTIVO"))
                .andExpect(jsonPath("$[0].invitationCode").doesNotExist())
                .andExpect(jsonPath("$[0].activeMembers").doesNotExist());
    }

    @Test
    void listsOnlyAuthorizedCompetitionsAndDistinctAssignmentsOfTheSameProblem() throws Exception {
        MockHttpSession member = login(practitioner);
        mvc.perform(get(competitionPath(alpha)).contextPath("/api").session(member))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].competitionId").value(alphaCompetition))
                .andExpect(jsonPath("$[0].teamId").value(alpha))
                .andExpect(jsonPath("$[0].accessKey").doesNotExist());
        var alphaResult = mvc.perform(get(competitionPath(alpha) + "/problems").contextPath("/api").session(member))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].problemId").value(problem))
                .andExpect(jsonPath("$[0].competitionId").value(alphaCompetition))
                .andExpect(jsonPath("$[0].title").value("Problema compartido")).andReturn();
        var betaResult = mvc.perform(get(competitionPath(beta) + "/problems").contextPath("/api").session(login(outsider)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].problemId").value(problem)).andReturn();
        assertThat(mapper.readTree(alphaResult.getResponse().getContentAsString()).get(0).get("competitionProblemId"))
                .isNotEqualTo(mapper.readTree(betaResult.getResponse().getContentAsString()).get(0).get("competitionProblemId"));
    }

    @Test
    void unrelatedCoachAndPractitionerCannotQueryAnotherTeam() throws Exception {
        for (Account account : new Account[]{coach, practitioner}) {
            for (String path : new String[]{competitionPath(beta), competitionPath(beta) + "/problems"}) {
                mvc.perform(get(path).contextPath("/api").session(login(account)))
                        .andExpect(status().isForbidden());
            }
        }
    }

    @Test
    void inactiveMembershipCannotReadListsAndIsNotOfferedInMyTeams() throws Exception {
        jdbc.update("UPDATE practicante_grupo SET estado='RETIRADO' WHERE id=?", membership);
        MockHttpSession session = login(practitioner);
        mvc.perform(get("/api/teams/me").contextPath("/api").session(session))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get(competitionPath(alpha)).contextPath("/api").session(session))
                .andExpect(status().isForbidden());
        mvc.perform(get(competitionPath(alpha) + "/problems").contextPath("/api").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void emptyListsAndMissingOrMalformedTeamsRemainDistinct() throws Exception {
        int empty = team(coach.id());
        MockHttpSession session = login(coach);
        for (String suffix : new String[]{"", "/problems"}) {
            mvc.perform(get(competitionPath(empty) + suffix).contextPath("/api").session(session))
                    .andExpect(status().isOk()).andExpect(content().json("[]"));
            mvc.perform(get(competitionPath(2147483647) + suffix).contextPath("/api").session(session))
                    .andExpect(status().isNotFound());
            mvc.perform(get(competitionPath(0) + suffix).contextPath("/api").session(session))
                    .andExpect(status().isBadRequest());
            mvc.perform(get("/api/competitions/teams/text" + suffix).contextPath("/api").session(session))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void anonymousRequestsNeverReadTeamData() throws Exception {
        for (String path : new String[]{"/api/teams/me", competitionPath(alpha), competitionPath(alpha) + "/problems"}) {
            mvc.perform(get(path).contextPath("/api")).andExpect(status().isUnauthorized());
        }
    }

    private Account account(String role) {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String email = suffix + "@example.test";
        int id = jdbc.queryForObject("INSERT INTO usuario(nombre_completo,correo,password_hash,rol,estado_cuenta) VALUES ('Navegacion prueba',?,?,?,'ACTIVO') RETURNING id",
                Integer.class, email, encoder.encode(PASSWORD), role);
        if (role.equals("COACH")) {
            jdbc.update("INSERT INTO coach(usuario_id,especialidad_principal) VALUES (?,'Grafos')", id);
        } else {
            jdbc.update("INSERT INTO practicante(usuario_id,codigo_estudiante,carrera) VALUES (?,?,'Ingenieria')", id, suffix.substring(0, 20));
        }
        return new Account(id, email);
    }

    private int team(int coachId) {
        return jdbc.queryForObject("INSERT INTO grupo_estudio(coach_id,nombre,nivel_esperado,codigo_invitacion) VALUES (?,'Equipo prueba','Div3',?) RETURNING id",
                Integer.class, coachId, UUID.randomUUID().toString().substring(0, 20));
    }

    private int competition(int team) {
        return jdbc.queryForObject("INSERT INTO competencia(grupo_id,nombre_evento,fecha_inicio,fecha_fin,estado) VALUES (?,'Competencia prueba',CURRENT_TIMESTAMP-INTERVAL '2 days',CURRENT_TIMESTAMP-INTERVAL '1 day','FINALIZADA') RETURNING id", Integer.class, team);
    }

    private void assign(int competition) {
        jdbc.update("INSERT INTO competencia_problema(competencia_id,problema_id,orden_letra) VALUES (?,?,'A')", competition, problem);
    }

    private MockHttpSession login(Account account) throws Exception {
        var body = mapper.createObjectNode().put("email", account.email()).put("password", PASSWORD);
        return (MockHttpSession) mvc.perform(post("/api/auth/login").contextPath("/api")
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }

    private String competitionPath(int team) {
        return "/api/competitions/teams/" + team;
    }

    private record Account(int id, String email) {
    }
}

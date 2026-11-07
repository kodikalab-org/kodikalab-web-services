package com.kodika.kodikalab.teams;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kodika.kodikalab.support.Bearer;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@EnabledIfEnvironmentVariable(named = "PROFILE_TEST_DB_URL", matches = ".+")
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
        return DriverManager.getConnection(System.getenv("PROFILE_TEST_DB_URL"),
                System.getenv().getOrDefault("PROFILE_TEST_DB_USER", "postgres"),
                System.getenv().getOrDefault("PROFILE_TEST_DB_PASSWORD", ""));
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry properties) throws SQLException {
        try (Connection connection = connection(); var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + SCHEMA);
            created = true;
        }
        properties.add("spring.datasource.url", () -> System.getenv("PROFILE_TEST_DB_URL"));
        properties.add("spring.datasource.username", () -> System.getenv().getOrDefault("PROFILE_TEST_DB_USER", "postgres"));
        properties.add("spring.datasource.password", () -> System.getenv().getOrDefault("PROFILE_TEST_DB_PASSWORD", ""));
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        properties.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        properties.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA);
        properties.add("spring.jpa.show-sql", () -> "false");
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        if (created) {
            try (Connection connection = connection(); var statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
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
        mvc.perform(get("/api/teams/me").contextPath("/api").with(Bearer.of(login(coach))).param("userId", "999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].groupId").value(alpha))
                .andExpect(jsonPath("$[0].activeMembers").value(1))
                .andExpect(jsonPath("$[0].invitationCode").exists())
                .andExpect(jsonPath("$[0].membership").doesNotExist())
                .andExpect(jsonPath("$[0].pendingRequests").value(0));
        mvc.perform(get("/api/teams/me").contextPath("/api").with(Bearer.of(login(practitioner))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].membership.membershipId").value(membership))
                .andExpect(jsonPath("$[0].membership.status").value("ACTIVO"))
                .andExpect(jsonPath("$[0].invitationCode").doesNotExist())
                .andExpect(jsonPath("$[0].activeMembers").doesNotExist());
    }

    @Test
    void unrelatedCoachAndPractitionerCannotQueryAnotherTeam() throws Exception {
        for (Account account : new Account[]{coach, practitioner}) {
            String session = login(account);
            for (MockHttpServletRequestBuilder request : teamQueries(beta)) {
                mvc.perform(request.contextPath("/api").with(Bearer.of(session))).andExpect(status().isForbidden());
            }
        }
    }

    @Test
    void inactiveMembershipCannotReadListsAndShowsItsStatusInMyTeams() throws Exception {
        jdbc.update("UPDATE practicante_grupo SET estado='RETIRADO' WHERE id=?", membership);
        String session = login(practitioner);
        mvc.perform(get("/api/teams/me").contextPath("/api").with(Bearer.of(session)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].membership.status").value("RETIRADO"));
        for (MockHttpServletRequestBuilder request : teamQueries(alpha)) {
            mvc.perform(request.contextPath("/api").with(Bearer.of(session))).andExpect(status().isForbidden());
        }
    }

    @Test
    void anonymousRequestsNeverReadTeamData() throws Exception {
        mvc.perform(get("/api/teams/me").contextPath("/api")).andExpect(status().isUnauthorized());
        for (MockHttpServletRequestBuilder request : teamQueries(alpha)) {
            mvc.perform(request.contextPath("/api")).andExpect(status().isUnauthorized());
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

    private String login(Account account) throws Exception {
        var body = mapper.createObjectNode().put("email", account.email()).put("password", PASSWORD);
        var result = mvc.perform(post("/api/auth/login").contextPath("/api")
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isOk()).andReturn();
        return Bearer.tokenFrom(result.getResponse().getContentAsString());
    }

    /** Las consultas por equipo que usa la navegación: competencias y problemas asignados. */
    private List<MockHttpServletRequestBuilder> teamQueries(int team) {
        return List.of(get("/api/competitions").param("teamId", String.valueOf(team)),
                get("/api/problems/assigned").param("teamId", String.valueOf(team)));
    }

    private record Account(int id, String email) {
    }
}

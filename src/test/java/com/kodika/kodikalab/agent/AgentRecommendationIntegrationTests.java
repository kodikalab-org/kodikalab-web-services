package com.kodika.kodikalab.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kodika.kodikalab.problems.codeforces.CodeforcesProblemCatalog;
import com.kodika.kodikalab.support.Bearer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * US-16 over HTTP: real security, tool loop, services and PostgreSQL. Only the model and Codeforces are replaced. Uses
 * a temporary schema in the test database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "AGENT_TEST_DB_URL", matches = ".+")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AgentRecommendationIntegrationTests {
    private static final String SCHEMA = "agent_test_" + UUID.randomUUID().toString().replace("-", "");
    private static boolean schemaCreated;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @MockitoBean ChatModel model;
    @MockitoBean CodeforcesProblemCatalog codeforces;

    private int coachId;
    private String coachEmail;
    private int teamId;
    private int fitting;
    private final AtomicInteger modelCalls = new AtomicInteger();

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(System.getenv("AGENT_TEST_DB_URL"),
                System.getenv().getOrDefault("AGENT_TEST_DB_USER", "postgres"),
                System.getenv().getOrDefault("AGENT_TEST_DB_PASSWORD", ""));
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) throws SQLException {
        try (Connection connection = connect(); var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + SCHEMA);
            schemaCreated = true;
        }
        registry.add("spring.datasource.url", () -> System.getenv("AGENT_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("AGENT_TEST_DB_USER", "postgres"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("AGENT_TEST_DB_PASSWORD", ""));
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA);
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        if (schemaCreated) {
            try (Connection connection = connect(); var statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
            }
        }
    }

    /** A team that solved a graph problem rated 1000 and failed its only tree problem. */
    @BeforeEach
    void createData() {
        coachEmail = email();
        coachId = coach(coachEmail);
        String practitionerEmail = email();
        int practitionerId = account("PRACTICANTE", practitionerEmail);
        jdbc.update("INSERT INTO practicante(usuario_id, codigo_estudiante, carrera) VALUES (?, ?, 'Ingeniería')",
                practitionerId, suffix().substring(0, 20));
        teamId = jdbc.queryForObject("""
                INSERT INTO grupo_estudio(coach_id, nombre, nivel_esperado, codigo_invitacion)
                VALUES (?, 'Equipo Agente', 'Div3', ?) RETURNING id
                """, Integer.class, coachId, suffix().substring(0, 20));
        int membership = jdbc.queryForObject(
                "INSERT INTO practicante_grupo(grupo_id, practicante_id) VALUES (?, ?) RETURNING id",
                Integer.class, teamId, practitionerId);
        int graphs = topic("graphs-" + suffix());
        int trees = topic("trees-" + suffix());
        int solved = problem("Solved graph", "1000", graphs);
        int failed = problem("Assigned tree", "1100", trees);
        resolution(membership, assign(solved), "ACCEPTED");
        resolution(membership, assign(failed), "WRONG_ANSWER");
        fitting = problem("Fitting tree", "1200", trees);
        problem("Too hard tree", "1900", trees);
        when(codeforces.problems()).thenReturn(List.of());
        when(model.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        modelCalls.set(0);
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> switch (modelCalls.incrementAndGet()) {
            case 1 -> answer(new AssistantMessage("{\"agent\":\"insights\"}"));
            case 2 -> answer(AssistantMessage.builder().content("").toolCalls(List.of(
                    new AssistantMessage.ToolCall("call-1", "function", "recommendProblems", "{}"))).build());
            default -> answer(new AssistantMessage("Ya puedes ver las propuestas"));
        });
    }

    @Test
    void theCoachReceivesAnUnassignedProblemThatFitsTheTeam() throws Exception {
        chat(login(coachEmail), teamId).andExpect(status().isOk())
                .andExpect(jsonPath("$.agent").value("insights"))
                .andExpect(jsonPath("$.message").value("Ya puedes ver las propuestas"))
                .andExpect(jsonPath("$.problems.length()").value(1))
                .andExpect(jsonPath("$.problems[0].problemId").value(fitting))
                .andExpect(jsonPath("$.problems[0].title").value("Fitting tree"))
                .andExpect(jsonPath("$.problems[0].source").value("CATALOG"))
                .andExpect(jsonPath("$.problems[0].reason").isNotEmpty())
                .andExpect(jsonPath("$.chart.labels.length()").value(2));
    }

    @Test
    void anotherCoachGetsNoDataFromTheTeam() throws Exception {
        String intruder = email();
        coach(intruder);

        chat(login(intruder), teamId).andExpect(status().isOk())
                .andExpect(jsonPath("$.problems").doesNotExist())
                .andExpect(jsonPath("$.chart").doesNotExist());
        assertThat(modelCalls.get()).isEqualTo(3);
    }

    @Test
    void anonymousRequestsNeverReachTheModel() throws Exception {
        mvc.perform(post("/api/agent/chat").contextPath("/api").contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"hola\"}")).andExpect(status().isUnauthorized());

        assertThat(modelCalls.get()).isZero();
    }

    private ResultActions chat(String token, int team) throws Exception {
        return mvc.perform(post("/api/agent/chat").contextPath("/api").with(Bearer.of(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Recomiéndame problemas\",\"teamId\":" + team + "}"));
    }

    private String login(String email) throws Exception {
        var result = mvc.perform(post("/api/auth/login").contextPath("/api").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Password123\"}"))
                .andExpect(status().isOk()).andReturn();
        return Bearer.tokenFrom(result.getResponse().getContentAsString());
    }

    private static ChatResponse answer(AssistantMessage message) {
        return new ChatResponse(List.of(new Generation(message)));
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String email() {
        return "test." + suffix() + "@gmail.com";
    }

    private int account(String role, String email) {
        return jdbc.queryForObject("""
                INSERT INTO usuario(nombre_completo, correo, password_hash, rol, estado_cuenta)
                VALUES ('Usuario Prueba', ?, ?, ?, 'ACTIVO') RETURNING id
                """, Integer.class, email, encoder.encode("Password123"), role);
    }

    private int coach(String email) {
        int id = account("COACH", email);
        jdbc.update("INSERT INTO coach(usuario_id, especialidad_principal) VALUES (?, 'Grafos')", id);
        return id;
    }

    private int topic(String name) {
        return jdbc.queryForObject("INSERT INTO tema(nombre) VALUES (?) RETURNING id", Integer.class, name);
    }

    private int problem(String title, String rating, int topicId) {
        int id = jdbc.queryForObject("""
                INSERT INTO problema(titulo, url_problema, dificultad_rating) VALUES (?, ?, ?) RETURNING id
                """, Integer.class, title, "https://example.com/" + suffix(), rating);
        jdbc.update("INSERT INTO problema_tema(problema_id, tema_id) VALUES (?, ?)", id, topicId);
        return id;
    }

    private int assign(int problemId) {
        int competition = jdbc.queryForObject("""
                INSERT INTO competencia(grupo_id, nombre_evento, fecha_inicio, fecha_fin, estado)
                VALUES (?, 'Competencia Prueba', CURRENT_TIMESTAMP - INTERVAL '6 hours',
                    CURRENT_TIMESTAMP - INTERVAL '1 hour', 'FINALIZADA') RETURNING id
                """, Integer.class, teamId);
        return jdbc.queryForObject("""
                INSERT INTO competencia_problema(competencia_id, problema_id, orden_letra)
                VALUES (?, ?, 'A') RETURNING id
                """, Integer.class, competition, problemId);
    }

    private void resolution(int membership, int assigned, String verdict) {
        jdbc.update("""
                INSERT INTO resolucion_problema(practicante_grupo_id, competencia_problema_id, veredicto)
                VALUES (?, ?, ?)
                """, membership, assigned, verdict);
    }
}

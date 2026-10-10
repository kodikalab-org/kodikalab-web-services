package com.kodika.kodikalab.assignments;

import com.jayway.jsonpath.JsonPath;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
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
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flujo completo de US-07 y US-08 por la API real contra PostgreSQL: catálogo, competencia, asignación y vista del
 * practicante. Solo las cuentas, el equipo y las membresías se preparan por SQL. Opt-in: requiere
 * {@code ASSIGNMENT_TEST_DB_URL} apuntando a una base exclusiva de pruebas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "ASSIGNMENT_TEST_DB_URL", matches = ".+")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AssignmentsIntegrationTests {
    private static final String SCHEMA = "assignment_test_" + UUID.randomUUID().toString().replace("-", "");
    private static boolean schemaCreated;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    String passwordHash;
    Account coach;
    Account outsiderCoach;
    Account member;
    Account secondMember;
    Account retired;
    Account outsider;
    int teamId;
    String coachSession;
    String memberSession;

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(System.getenv("ASSIGNMENT_TEST_DB_URL"),
                System.getenv().getOrDefault("ASSIGNMENT_TEST_DB_USER", "postgres"),
                System.getenv().getOrDefault("ASSIGNMENT_TEST_DB_PASSWORD", ""));
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) throws SQLException {
        try (Connection connection = connect(); var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + SCHEMA);
            schemaCreated = true;
        }
        registry.add("spring.datasource.url", () -> System.getenv("ASSIGNMENT_TEST_DB_URL"));
        registry.add("spring.datasource.username",
                () -> System.getenv().getOrDefault("ASSIGNMENT_TEST_DB_USER", "postgres"));
        registry.add("spring.datasource.password",
                () -> System.getenv().getOrDefault("ASSIGNMENT_TEST_DB_PASSWORD", ""));
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
        outsiderCoach = account("COACH");
        member = account("PRACTICANTE");
        secondMember = account("PRACTICANTE");
        retired = account("PRACTICANTE");
        outsider = account("PRACTICANTE");
        teamId = team(coach.id());
        membership(teamId, member.id(), "ACTIVO");
        membership(teamId, secondMember.id(), "ACTIVO");
        membership(teamId, retired.id(), "RETIRADO");
        coachSession = login(coach);
        memberSession = login(member);
    }

    // ---- flujo completo

    @Test
    void coachBuildsTheCatalogAssignsProblemsAndThePractitionerTracksThemUntilSolved() throws Exception {
        int graphs = createProblem("Flujo Grafos", "CF-FLOW-1", "1400", "Grafos", "BFS");
        int dp = createProblem("Flujo DP", "CF-FLOW-2", "800", "dp");
        int competitionId = createCompetition("Simulacro flujo", "PROGRAMADA");

        String assigned = body(assign(coachSession, competitionId, "[{\"problemId\":" + graphs
                + ",\"score\":100,\"balloonColor\":\"#00ff00\"},{\"problemId\":" + dp + "}]")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.teamId").value(teamId))
                .andExpect(jsonPath("$.assigned[0].letter").value("A"))
                .andExpect(jsonPath("$.assigned[0].title").value("Flujo Grafos"))
                .andExpect(jsonPath("$.assigned[0].balloonColor").value("#00FF00"))
                .andExpect(jsonPath("$.assigned[1].letter").value("B"))
                .andExpect(jsonPath("$.assigned[1].score").value(1)));
        int firstAssignment = JsonPath.read(assigned, "$.assigned[0].competitionProblemId");

        mvc.perform(get("/api/problems/assigned").contextPath("/api").with(Bearer.of(memberSession))
                        .param("teamId", "" + teamId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].letter").value("A"))
                .andExpect(jsonPath("$.items[0].status").value("SIN_INTENTOS"))
                .andExpect(jsonPath("$.items[0].attemptCount").value(0))
                .andExpect(jsonPath("$.items[0].competition.status").value("PROGRAMADA"))
                .andExpect(jsonPath("$.items[0].competition.durationMinutes").value(300))
                .andExpect(jsonPath("$.items[0].problem.topics.length()").value(2))
                .andExpect(jsonPath("$.items[1].problem.topics[0]").value("dp"));

        mvc.perform(post("/api/competitions/teams/" + teamId + "/problems/" + firstAssignment + "/resolutions")
                        .contextPath("/api").with(Bearer.of(memberSession)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"Java\",\"evidenceUrl\":\"https://codeforces.com/s/1\"}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/problems/assigned").contextPath("/api").with(Bearer.of(memberSession))
                        .param("teamId", "" + teamId).param("status", "RESUELTO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].competitionProblemId").value(firstAssignment))
                .andExpect(jsonPath("$.items[0].status").value("RESUELTO"))
                .andExpect(jsonPath("$.items[0].attemptCount").value(1))
                .andExpect(jsonPath("$.items[0].lastAttempt.language").value("Java"));

        mvc.perform(get("/api/problems/assigned/" + firstAssignment).contextPath("/api").with(Bearer.of(memberSession)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignment.status").value("RESUELTO"))
                .andExpect(jsonPath("$.attempts.length()").value(1))
                .andExpect(jsonPath("$.attempts[0].verdict").value("ACCEPTED"))
                .andExpect(jsonPath("$.attempts[0].evidenceUrl").value("https://codeforces.com/s/1"));

        // El avance de un compañero es independiente: no ve el intento de otro integrante.
        mvc.perform(get("/api/problems/assigned").contextPath("/api").with(Bearer.of(login(secondMember)))
                        .param("teamId", "" + teamId))
                .andExpect(jsonPath("$.items[0].status").value("SIN_INTENTOS"));
    }

    // ---- catálogo

    @Test
    void catalogRegistrationNormalizesStoresTopicsOnceAndRejectsDuplicates() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String created = body(send("/api/problems", coachSession, """
                {"title":"  Catálogo %s ","url":"https://codeforces.com/problemset/problem/%s/A",
                 "sourceCode":"CF-%s","difficultyRating":"1200","timeLimitMs":2000,"memoryLimitMb":512,
                 "topics":["Grafos %s"," grafos %s ","DP %s"]}""".formatted(suffix, suffix, suffix, suffix, suffix,
                suffix))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Catálogo " + suffix))
                .andExpect(jsonPath("$.sourcePlatform").value("CODEFORCES"))
                .andExpect(jsonPath("$.timeLimitMs").value(2000))
                .andExpect(jsonPath("$.topics.length()").value(2)));
        int id = JsonPath.read(created, "$.id");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM problema_tema WHERE problema_id = ?", Integer.class, id))
                .isEqualTo(2);

        // Reutiliza el tema existente sin distinguir mayúsculas y no crea otro.
        send("/api/problems", coachSession, ("{\"title\":\"Otro %s\",\"url\":\"https://atcoder.jp/o/%s\","
                + "\"sourcePlatform\":\"ATCODER\",\"topics\":[\"GRAFOS %s\"]}").formatted(suffix, suffix, suffix))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tema WHERE lower(nombre) = lower(?)", Integer.class,
                "Grafos " + suffix)).isEqualTo(1);

        send("/api/problems", coachSession, ("{\"title\":\"Copia\",\"url\":\"HTTPS://CODEFORCES.COM/problemset/problem/"
                + suffix + "/A\"}")).andExpect(status().isConflict()).andExpect(jsonPath("$.errors.url").exists());
        send("/api/problems", coachSession, ("{\"title\":\"Copia\",\"url\":\"https://otro.example/" + suffix
                + "\",\"sourceCode\":\"cf-" + suffix + "\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.sourceCode").exists());
        // El mismo código en otra plataforma sí es otro problema.
        send("/api/problems", coachSession, ("{\"title\":\"Otra plataforma\",\"url\":\"https://otro.example/p/"
                + suffix + "\",\"sourcePlatform\":\"CSES\",\"sourceCode\":\"CF-" + suffix + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void onlyAnActiveCoachRegistersProblemsAndInvalidDataIsNotStored() throws Exception {
        String title = "Inválido " + UUID.randomUUID();
        send("/api/problems", memberSession, "{\"title\":\"" + title + "\",\"url\":\"https://x.example/a\"}")
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/problems").contextPath("/api").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"url\":\"https://x.example/a\"}"))
                .andExpect(status().isUnauthorized());
        send("/api/problems", coachSession, "{\"title\":\"" + title + "\",\"url\":\"ftp://x.example/a\","
                + "\"timeLimitMs\":0,\"topics\":[\" \"]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.url").exists()).andExpect(jsonPath("$.errors.timeLimitMs").exists())
                .andExpect(jsonPath("$.errors['topics[0]']").exists());
        jdbc.update("UPDATE usuario SET estado_cuenta = 'SUSPENDIDO' WHERE id = ?", coach.id());
        send("/api/problems", coachSession, "{\"title\":\"" + title + "\",\"url\":\"https://x.example/a\"}")
                .andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM problema WHERE titulo = ?", Integer.class, title))
                .isZero();
    }

    @Test
    void catalogSearchPaginatesFiltersAndTreatsWildcardsAsLiteralText() throws Exception {
        String tag = "BUS" + UUID.randomUUID().toString().substring(0, 6);
        createProblem(tag + " Interés 100% compuesto", "S-" + tag + "-1", "800", "Matemática " + tag);
        createProblem(tag + " snake_case", "S-" + tag + "-2", "1400", "Matemática " + tag, "Strings " + tag);
        createProblem(tag + " Sumas", "S-" + tag + "-3", "800");
        int topicId = jdbc.queryForObject("SELECT id FROM tema WHERE nombre = ?", Integer.class, "Matemática " + tag);

        mvc.perform(get("/api/problems").contextPath("/api").with(Bearer.of(memberSession)).param("q", tag)
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items[0].title").value(tag + " Interés 100% compuesto"));
        mvc.perform(get("/api/problems").contextPath("/api").with(Bearer.of(memberSession)).param("q", tag)
                        .param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.page").value(1));
        // % y _ se buscan como texto, no como comodines.
        mvc.perform(get("/api/problems").contextPath("/api").with(Bearer.of(memberSession)).param("q", "100%"))
                .andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(get("/api/problems").contextPath("/api").with(Bearer.of(memberSession)).param("q", tag + " snake_c"))
                .andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(get("/api/problems").contextPath("/api").with(Bearer.of(memberSession)).param("q", tag + "_"))
                .andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(get("/api/problems").contextPath("/api").with(Bearer.of(memberSession)).param("q", "s-" + tag + "-3"))
                .andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(get("/api/problems").contextPath("/api").with(Bearer.of(memberSession)).param("q", tag)
                        .param("difficulty", "800"))
                .andExpect(jsonPath("$.totalItems").value(2));
        mvc.perform(get("/api/problems").contextPath("/api").with(Bearer.of(memberSession)).param("q", tag)
                        .param("topicId", "" + topicId))
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[1].topics.length()").value(2));
        mvc.perform(get("/api/problems").contextPath("/api").with(Bearer.of(memberSession)).param("q", tag)
                        .param("platform", "CSES"))
                .andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(get("/api/problems").contextPath("/api").param("q", tag)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/problems").contextPath("/api").with(Bearer.of(memberSession)).param("size", "101"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.size").exists());
    }

    // ---- US-07: reglas de asignación

    @Test
    void anInvalidAssignmentChangesNothingAndNamesTheCause() throws Exception {
        int first = createProblem("Reglas uno", null, null);
        int second = createProblem("Reglas dos", null, null);
        int competitionId = createCompetition("Simulacro reglas", "PROGRAMADA");
        assign(coachSession, competitionId, "[{\"problemId\":" + first + ",\"letter\":\"A\"}]")
                .andExpect(status().isCreated());

        // Ya asignado + letra en uso: se informan juntos y no se registra el segundo problema.
        assign(coachSession, competitionId, "[{\"problemId\":" + first + "},{\"problemId\":" + second
                + ",\"letter\":\"a\"}]").andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors['problems[0].problemId']").exists())
                .andExpect(jsonPath("$.errors['problems[1].letter']").exists());
        // Un problema que no existe en el catálogo invalida toda la solicitud.
        assign(coachSession, competitionId, "[{\"problemId\":" + second + "},{\"problemId\":2147483000}]")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors['problems[1].problemId']").exists());
        assign(coachSession, competitionId, "[{\"problemId\":" + second + ",\"score\":0,\"balloonColor\":\"rojo\"}]")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors['problems[0].score']").exists())
                .andExpect(jsonPath("$.errors['problems[0].balloonColor']").exists());

        assertThat(countAssignments(competitionId)).isEqualTo(1);
        assign(coachSession, competitionId, "[{\"problemId\":" + second + "}]").andExpect(status().isCreated())
                .andExpect(jsonPath("$.assigned[0].letter").value("B"));
    }

    @Test
    void onlyTheResponsibleCoachAssignsAndFinishedCompetitionsAreClosed() throws Exception {
        int problem = createProblem("Permisos", null, null);
        int competitionId = createCompetition("Simulacro permisos", "PROGRAMADA");
        String item = "[{\"problemId\":" + problem + "}]";

        assign(login(outsiderCoach), competitionId, item).andExpect(status().isForbidden());
        assign(memberSession, competitionId, item).andExpect(status().isForbidden());
        mvc.perform(post("/api/problems/assign").contextPath("/api").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"competitionId\":" + competitionId + ",\"problems\":" + item + "}"))
                .andExpect(status().isUnauthorized());
        assign(coachSession, Integer.MAX_VALUE, item).andExpect(status().isNotFound());
        int finished = createCompetition("Simulacro cerrado", "FINALIZADA");
        assign(coachSession, finished, item).andExpect(status().isConflict());
        int running = createCompetition("Simulacro en curso", "EN_CURSO");
        assign(coachSession, running, item).andExpect(status().isCreated());

        assertThat(countAssignments(competitionId)).isZero();
        assertThat(countAssignments(finished)).isZero();
        jdbc.update("UPDATE usuario SET estado_cuenta = 'SUSPENDIDO' WHERE id = ?", coach.id());
        assign(coachSession, competitionId, item).andExpect(status().isForbidden());
    }

    @Test
    void concurrentAssignmentsNeverShareALetterOrAssignTheSameProblemTwice() throws Exception {
        int a = createProblem("Concurrente A", null, null);
        int b = createProblem("Concurrente B", null, null);
        int competitionId = createCompetition("Simulacro concurrente", "PROGRAMADA");

        List<Integer> distinct = concurrently(
                () -> statusOf(assign(coachSession, competitionId, "[{\"problemId\":" + a + "}]")),
                () -> statusOf(assign(coachSession, competitionId, "[{\"problemId\":" + b + "}]")));
        assertThat(distinct).containsExactly(201, 201);
        assertThat(jdbc.queryForList("SELECT orden_letra FROM competencia_problema WHERE competencia_id = ? "
                + "ORDER BY orden_letra", String.class, competitionId)).containsExactly("A", "B");

        int c = createProblem("Concurrente C", null, null);
        List<Integer> same = concurrently(
                () -> statusOf(assign(coachSession, competitionId, "[{\"problemId\":" + c + "}]")),
                () -> statusOf(assign(coachSession, competitionId, "[{\"problemId\":" + c + "}]")));
        assertThat(same).containsExactly(201, 409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM competencia_problema WHERE competencia_id = ? "
                + "AND problema_id = ?", Integer.class, competitionId, c)).isEqualTo(1);
    }

    // ---- US-08: acceso y protección de datos

    @Test
    void onlyTeamMembersAndTheResponsibleCoachSeeTheAssignments() throws Exception {
        int problem = createProblem("Acceso", null, null);
        int competitionId = createCompetition("Simulacro acceso", "PROGRAMADA");
        int assignmentId = JsonPath.read(body(assign(coachSession, competitionId, "[{\"problemId\":" + problem + "}]")),
                "$.assigned[0].competitionProblemId");
        String team = "" + teamId;

        for (Account denied : List.of(outsider, retired, outsiderCoach)) {
            String session = login(denied);
            mvc.perform(get("/api/problems/assigned").contextPath("/api").with(Bearer.of(session)).param("teamId", team))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/problems/assigned/" + assignmentId).contextPath("/api").with(Bearer.of(session)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/problems/assigned").contextPath("/api").param("teamId", team))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/problems/assigned").contextPath("/api").with(Bearer.of(memberSession)).param("teamId", "2147483000"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/problems/assigned").contextPath("/api").with(Bearer.of(memberSession)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.teamId").exists());
        mvc.perform(get("/api/problems/assigned/2147483000").contextPath("/api").with(Bearer.of(memberSession)))
                .andExpect(status().isNotFound());

        // El coach responsable ve la asignación del equipo, sin avance personal.
        mvc.perform(get("/api/problems/assigned").contextPath("/api").with(Bearer.of(coachSession)).param("teamId", team))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].status").doesNotExist())
                .andExpect(jsonPath("$.items[0].attemptCount").doesNotExist());
        mvc.perform(get("/api/problems/assigned").contextPath("/api").with(Bearer.of(coachSession)).param("teamId", team)
                        .param("status", "RESUELTO"))
                .andExpect(status().isBadRequest());

        // Una suspensión posterior corta el acceso aunque la sesión siga abierta.
        jdbc.update("UPDATE usuario SET estado_cuenta = 'SUSPENDIDO' WHERE id = ?", member.id());
        mvc.perform(get("/api/problems/assigned").contextPath("/api").with(Bearer.of(memberSession)).param("teamId", team))
                .andExpect(status().isForbidden());
    }

    @Test
    void filteringAndSortingNeverChangeTheAssignmentsOrTheProgress() throws Exception {
        int a = createProblem("Orden Zeta", null, "1400");
        int b = createProblem("Orden alfa", null, "800");
        int competitionId = createCompetition("Simulacro orden", "PROGRAMADA");
        assign(coachSession, competitionId, "[{\"problemId\":" + a + "},{\"problemId\":" + b + "}]")
                .andExpect(status().isCreated());
        int before = countAssignments(competitionId);
        int attemptsBefore = jdbc.queryForObject("SELECT count(*) FROM resolucion_problema", Integer.class);

        String team = "" + teamId;
        mvc.perform(get("/api/problems/assigned").contextPath("/api").with(Bearer.of(memberSession)).param("teamId", team)
                        .param("sort", "title").param("order", "asc"))
                .andExpect(jsonPath("$.items[0].problem.title").value("Orden alfa"))
                .andExpect(jsonPath("$.items[1].problem.title").value("Orden Zeta"));
        mvc.perform(get("/api/problems/assigned").contextPath("/api").with(Bearer.of(memberSession)).param("teamId", team)
                        .param("sort", "difficulty").param("order", "desc"))
                .andExpect(jsonPath("$.items[0].problem.difficultyRating").value("1400"));
        mvc.perform(get("/api/problems/assigned").contextPath("/api").with(Bearer.of(memberSession)).param("teamId", team)
                        .param("q", "ZETA").param("status", "SIN_INTENTOS"))
                .andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/problems/assigned").contextPath("/api").with(Bearer.of(memberSession)).param("teamId", team)
                        .param("sort", "azar")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.sort").exists());
        mvc.perform(get("/api/problems/assigned").contextPath("/api").with(Bearer.of(memberSession)).param("teamId", team)
                        .param("status", "INVENTADO")).andExpect(status().isBadRequest());

        assertThat(countAssignments(competitionId)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM resolucion_problema", Integer.class))
                .isEqualTo(attemptsBefore);
    }

    @Test
    void aPractitionerCannotReadAnotherTeamsAssignmentsEvenKnowingItsId() throws Exception {
        String otherCoach = login(outsiderCoach);
        int otherTeam = jdbc.queryForObject("""
                INSERT INTO grupo_estudio(coach_id, nombre, nivel_esperado, codigo_invitacion)
                VALUES (?, 'Equipo Ajeno', 'Div3', ?) RETURNING id
                """, Integer.class, outsiderCoach.id(), UUID.randomUUID().toString().substring(0, 20));
        int problem = createProblem("Ajeno", null, null);
        int competitionId = jdbc.queryForObject("""
                INSERT INTO competencia(grupo_id, nombre_evento, fecha_inicio, fecha_fin, estado)
                VALUES (?, 'Competencia ajena', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '5 hours',
                    'PROGRAMADA') RETURNING id
                """, Integer.class, otherTeam);
        int foreignAssignment = JsonPath.read(body(assign(otherCoach, competitionId, "[{\"problemId\":" + problem + "}]")),
                "$.assigned[0].competitionProblemId");

        mvc.perform(get("/api/problems/assigned/" + foreignAssignment).contextPath("/api").with(Bearer.of(memberSession)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/problems/assigned").contextPath("/api").with(Bearer.of(memberSession))
                        .param("teamId", "" + otherTeam)).andExpect(status().isForbidden());
        mvc.perform(get("/api/problems/assigned").contextPath("/api").with(Bearer.of(memberSession))
                        .param("teamId", "" + teamId)).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void conditionsExposeTheCompetitionButNeverItsAccessKey() throws Exception {
        int problem = createProblem("Privada", null, null);
        String created = body(send("/api/competitions", coachSession, "{\"teamId\":" + teamId
                + ",\"eventName\":\"Simulacro privado\",\"accessType\":\"PRIVADO_PASS\",\"accessKey\":\"secreto-123\","
                + "\"startsAt\":\"2026-12-01T09:00:00-05:00\",\"endsAt\":\"2026-12-01T14:00:00-05:00\"}")
                .andExpect(status().isCreated()));
        int competitionId = JsonPath.read(created, "$.id");
        assign(coachSession, competitionId, "[{\"problemId\":" + problem + "}]").andExpect(status().isCreated());

        mvc.perform(get("/api/problems/assigned").contextPath("/api").with(Bearer.of(memberSession))
                        .param("teamId", "" + teamId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].competition.accessType").value("PRIVADO_PASS"))
                .andExpect(content().string(not(containsString("secreto"))))
                .andExpect(content().string(not(containsString("$2a"))));
    }

    // ---- utilidades

    private ResultActions send(String path, String session, String json) throws Exception {
        return mvc.perform(post(path).contextPath("/api").with(Bearer.of(session))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions assign(String session, int competitionId, String problemsJson) throws Exception {
        return send("/api/problems/assign", session,
                "{\"competitionId\":" + competitionId + ",\"problems\":" + problemsJson + "}");
    }

    private int createProblem(String title, String sourceCode, String difficulty, String... topics) throws Exception {
        String suffix = UUID.randomUUID().toString();
        StringBuilder json = new StringBuilder("{\"title\":\"" + title + "\",\"url\":\"https://judge.example/p/" + suffix
                + "\"");
        if (sourceCode != null) {
            json.append(",\"sourceCode\":\"").append(sourceCode).append("\"");
        }
        if (difficulty != null) {
            json.append(",\"difficultyRating\":\"").append(difficulty).append("\"");
        }
        if (topics.length > 0) {
            List<String> quoted = new ArrayList<>();
            for (String topic : topics) {
                quoted.add("\"" + topic + "\"");
            }
            json.append(",\"topics\":[").append(String.join(",", quoted)).append("]");
        }
        json.append("}");
        return JsonPath.read(body(send("/api/problems", coachSession, json.toString())
                .andExpect(status().isCreated())), "$.id");
    }

    private int createCompetition(String name, String state) throws Exception {
        String unique = name + " " + UUID.randomUUID().toString().substring(0, 6);
        return JsonPath.read(body(send("/api/competitions", coachSession, "{\"teamId\":" + teamId
                + ",\"eventName\":\"" + unique + "\",\"status\":\"" + state + "\","
                + "\"startsAt\":\"2026-12-01T09:00:00-05:00\",\"endsAt\":\"2026-12-01T14:00:00-05:00\"}")
                .andExpect(status().isCreated())), "$.id");
    }

    private static String body(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString();
    }

    private static int statusOf(ResultActions actions) {
        return actions.andReturn().getResponse().getStatus();
    }

    private int countAssignments(int competitionId) {
        return jdbc.queryForObject("SELECT count(*) FROM competencia_problema WHERE competencia_id = ?",
                Integer.class, competitionId);
    }

    private List<Integer> concurrently(Callable<Integer> first, Callable<Integer> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> task : List.of(first, second)) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(30, TimeUnit.SECONDS));
            }
            Collections.sort(statuses);
            return statuses;
        } finally {
            executor.shutdownNow();
        }
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

    private int team(int coachId) {
        return jdbc.queryForObject("""
                INSERT INTO grupo_estudio(coach_id, nombre, nivel_esperado, codigo_invitacion)
                VALUES (?, 'Equipo Prueba', 'Div3', ?) RETURNING id
                """, Integer.class, coachId, UUID.randomUUID().toString().substring(0, 20));
    }

    private void membership(int groupId, int userId, String state) {
        jdbc.update("INSERT INTO practicante_grupo(grupo_id, practicante_id, estado) VALUES (?, ?, ?)", groupId, userId,
                state);
    }

    private String login(Account account) throws Exception {
        var result = mvc.perform(post("/api/auth/login").contextPath("/api").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + account.email() + "\",\"password\":\"Password123\"}"))
                .andExpect(status().isOk()).andReturn();
        String token = Bearer.tokenFrom(result.getResponse().getContentAsString());
        assertThat(token).isNotBlank();
        return token;
    }

    private record Account(int id, String email) {
    }
}

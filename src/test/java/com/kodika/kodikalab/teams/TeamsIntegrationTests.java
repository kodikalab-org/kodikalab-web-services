
package com.kodika.kodikalab.teams;

import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserRepository;
import com.kodika.kodikalab.users.UserStatus;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas HTTP y PostgreSQL para US04, US05 y US06.
 * Utiliza un esquema temporal dentro de la base de pruebas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "PROFILE_TEST_DB_URL", matches = ".+")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TeamsIntegrationTests {

    private static final String SCHEMA = "teams_test_"
            + UUID.randomUUID().toString().replace("-", "");

    private static final String PASSWORD = "Password123";

    private static boolean schemaCreated;

    @Autowired
    TestRestTemplate http;

    @Autowired
    UserRepository users;

    @Autowired
    PasswordEncoder encoder;

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(
                System.getenv("PROFILE_TEST_DB_URL"),
                System.getenv().getOrDefault("PROFILE_TEST_DB_USER", "postgres"),
                System.getenv().getOrDefault("PROFILE_TEST_DB_PASSWORD", "")
        );
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry)
            throws SQLException {

        try (Connection connection = connect();
             var statement = connection.createStatement()) {

            statement.execute("CREATE SCHEMA " + SCHEMA);
            schemaCreated = true;
        }

        registry.add("spring.datasource.url",
                () -> System.getenv("PROFILE_TEST_DB_URL"));

        registry.add("spring.datasource.username",
                () -> System.getenv()
                        .getOrDefault("PROFILE_TEST_DB_USER", "postgres"));

        registry.add("spring.datasource.password",
                () -> System.getenv()
                        .getOrDefault("PROFILE_TEST_DB_PASSWORD", ""));

        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");

        registry.add("spring.jpa.properties.hibernate.default_schema",
                () -> SCHEMA);

        registry.add("spring.datasource.hikari.connection-init-sql",
                () -> "SET search_path TO " + SCHEMA);

        registry.add("spring.jpa.show-sql", () -> "false");

    }

    @AfterAll
    static void cleanUp() throws SQLException {
        if (schemaCreated) {
            try (Connection connection = connect();
                 var statement = connection.createStatement()) {

                statement.execute(
                        "DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE"
                );
            }
        }
    }

    @Test
    void coachCreatesGroupAndGroupAppearsInList() {
        User coach = account(Role.COACH);
        String session = login(coach);

        var profileResponse = http.exchange(
                "/users/me",
                HttpMethod.PUT,
                new HttpEntity<>(
                        Map.of(
                                "especialidadPrincipal", "Grafos",
                                "organizacionClub", "Club ICPC",
                                "aniosExperiencia", 4,
                                "presentacion", "Entrenador de programación"
                        ),
                        headers(session)
                ),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(profileResponse.getStatusCode().value())
                .isEqualTo(200);

        var created = http.exchange(
                "/teams",
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "name", "Entrenamiento de Grafos",
                                "description", "Preparación para ICPC",
                                "expectedLevel", "Div3",
                                "maxCapacity", 15,
                                "sessionSchedule", "Lunes 18:00",
                                "visibility", "PUBLICO"
                        ),
                        headers(session)
                ),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(created.getStatusCode().value())
                .isEqualTo(201);

        assertThat(created.getBody())
                .containsEntry("name", "Entrenamiento de Grafos")
                .containsKey("groupId")
                .containsKey("invitationCode");

        var listed = http.exchange(
                "/teams",
                HttpMethod.GET,
                new HttpEntity<>(headers(session)),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}
        );

        assertThat(listed.getStatusCode().value())
                .isEqualTo(200);

        assertThat(listed.getBody())
                .anySatisfy(group ->
                        assertThat(group)
                                .containsEntry(
                                        "name",
                                        "Entrenamiento de Grafos"
                                )
                );
    }

    @Test
    void requestWithoutTokenCannotCreateGroup() {
        var response = http.exchange(
                "/teams",
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "name", "Grupo sin autorización",
                                "expectedLevel", "Div3",
                                "maxCapacity", 10,
                                "visibility", "PUBLICO"
                        ),
                        headers(null)
                ),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(response.getStatusCode().value())
                .isEqualTo(401);
    }

    @Test
    void practitionerRequestsProtectedGroupAndGetsPendingStatus() {
        User coach = account(Role.COACH);
        String coachSession = login(coach);

        var coachProfile = http.exchange(
                "/users/me",
                HttpMethod.PUT,
                new HttpEntity<>(
                        Map.of(
                                "especialidadPrincipal", "Grafos",
                                "organizacionClub", "Club ICPC",
                                "aniosExperiencia", 4,
                                "presentacion", "Entrenador ICPC"
                        ),
                        headers(coachSession)
                ),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(coachProfile.getStatusCode().value()).isEqualTo(200);

        var created = http.exchange(
                "/teams",
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "name", "Grupo Protegido",
                                "expectedLevel", "Div3",
                                "maxCapacity", 5,
                                "visibility", "PROTEGIDO"
                        ),
                        headers(coachSession)
                ),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(created.getStatusCode().value()).isEqualTo(201);

        Integer groupId = (Integer) created.getBody().get("groupId");

        User practitioner = account(Role.PRACTICANTE);
        String practitionerSession = login(practitioner);

        var practitionerProfile = http.exchange(
                "/users/me",
                HttpMethod.PUT,
                new HttpEntity<>(
                        Map.of(
                                "codigoEstudiante", "T" + UUID.randomUUID().toString().substring(0, 8),
                                "carrera", "Ingenieria de Software",
                                "cicloAcademico", 5,
                                "nivelCompetitivo", "INTERMEDIO"
                        ),
                        headers(practitionerSession)
                ),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(practitionerProfile.getStatusCode().value()).isEqualTo(200);

        var joined = http.exchange(
                "/teams/" + groupId + "/join",
                HttpMethod.POST,
                new HttpEntity<>(headers(practitionerSession)),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(joined.getStatusCode().value()).isEqualTo(201);
        assertThat(joined.getBody()).containsEntry("status", "PENDIENTE");

        var pending = http.exchange(
                "/teams/" + groupId + "/memberships?status=PENDIENTE",
                HttpMethod.GET,
                new HttpEntity<>(headers(coachSession)),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}
        );

        assertThat(pending.getStatusCode().value()).isEqualTo(200);
        assertThat(pending.getBody()).hasSize(1);
        assertThat(pending.getBody().get(0))
                .containsEntry("status", "PENDIENTE")
                .containsEntry("practitionerId", practitioner.getId());
    }


    @Test
    void coachAcceptsPendingMembership() {
        User coach = account(Role.COACH);
        String coachSession = login(coach);

        var coachProfile = http.exchange(
                "/users/me",
                HttpMethod.PUT,
                new HttpEntity<>(
                        Map.of(
                                "especialidadPrincipal", "Grafos",
                                "organizacionClub", "Club ICPC",
                                "aniosExperiencia", 4,
                                "presentacion", "Entrenador ICPC"
                        ),
                        headers(coachSession)
                ),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(coachProfile.getStatusCode().value()).isEqualTo(200);

        var created = http.exchange(
                "/teams",
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "name", "Grupo para Aceptacion",
                                "expectedLevel", "Div3",
                                "maxCapacity", 5,
                                "visibility", "PROTEGIDO"
                        ),
                        headers(coachSession)
                ),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(created.getStatusCode().value()).isEqualTo(201);

        Integer groupId = (Integer) created.getBody().get("groupId");

        User practitioner = account(Role.PRACTICANTE);
        String practitionerSession = login(practitioner);

        var practitionerProfile = http.exchange(
                "/users/me",
                HttpMethod.PUT,
                new HttpEntity<>(
                        Map.of(
                                "codigoEstudiante",
                                "T" + UUID.randomUUID().toString().substring(0, 8),
                                "carrera", "Ingenieria de Software",
                                "cicloAcademico", 5,
                                "nivelCompetitivo", "INTERMEDIO"
                        ),
                        headers(practitionerSession)
                ),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(practitionerProfile.getStatusCode().value()).isEqualTo(200);

        var joined = http.exchange(
                "/teams/" + groupId + "/join",
                HttpMethod.POST,
                new HttpEntity<>(headers(practitionerSession)),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(joined.getStatusCode().value()).isEqualTo(201);
        assertThat(joined.getBody()).containsEntry("status", "PENDIENTE");

        Integer membershipId =
                (Integer) joined.getBody().get("membershipId");

        var accepted = http.exchange(
                "/teams/" + groupId + "/memberships/" + membershipId,
                HttpMethod.PATCH,
                new HttpEntity<>(
                        Map.of("decision", "ACEPTAR"),
                        headers(coachSession)
                ),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(accepted.getStatusCode().value()).isEqualTo(200);
        assertThat(accepted.getBody())
                .containsEntry("membershipId", membershipId)
                .containsEntry("status", "ACTIVO");

        var pending = http.exchange(
                "/teams/" + groupId + "/memberships?status=PENDIENTE",
                HttpMethod.GET,
                new HttpEntity<>(headers(coachSession)),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}
        );

        assertThat(pending.getStatusCode().value()).isEqualTo(200);
        assertThat(pending.getBody()).isEmpty();
    }


    @Test
    void coachRejectsPendingMembership() {
        User coach = account(Role.COACH);
        String coachSession = login(coach);

        var coachProfile = http.exchange(
                "/users/me",
                HttpMethod.PUT,
                new HttpEntity<>(
                        Map.of(
                                "especialidadPrincipal", "Grafos",
                                "organizacionClub", "Club ICPC",
                                "aniosExperiencia", 4,
                                "presentacion", "Entrenador ICPC"
                        ),
                        headers(coachSession)
                ),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(coachProfile.getStatusCode().value()).isEqualTo(200);

        var created = http.exchange(
                "/teams",
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "name", "Grupo para Rechazo",
                                "expectedLevel", "Div3",
                                "maxCapacity", 5,
                                "visibility", "PROTEGIDO"
                        ),
                        headers(coachSession)
                ),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(created.getStatusCode().value()).isEqualTo(201);

        Integer groupId = (Integer) created.getBody().get("groupId");

        User practitioner = account(Role.PRACTICANTE);
        String practitionerSession = login(practitioner);

        var practitionerProfile = http.exchange(
                "/users/me",
                HttpMethod.PUT,
                new HttpEntity<>(
                        Map.of(
                                "codigoEstudiante",
                                "T" + UUID.randomUUID().toString().substring(0, 8),
                                "carrera", "Ingenieria de Software",
                                "cicloAcademico", 5,
                                "nivelCompetitivo", "INTERMEDIO"
                        ),
                        headers(practitionerSession)
                ),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(practitionerProfile.getStatusCode().value()).isEqualTo(200);

        var joined = http.exchange(
                "/teams/" + groupId + "/join",
                HttpMethod.POST,
                new HttpEntity<>(headers(practitionerSession)),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(joined.getStatusCode().value()).isEqualTo(201);
        assertThat(joined.getBody()).containsEntry("status", "PENDIENTE");

        Integer membershipId =
                (Integer) joined.getBody().get("membershipId");

        var rejected = http.exchange(
                "/teams/" + groupId + "/memberships/" + membershipId,
                HttpMethod.PATCH,
                new HttpEntity<>(
                        Map.of("decision", "RECHAZAR"),
                        headers(coachSession)
                ),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(rejected.getStatusCode().value()).isEqualTo(200);
        assertThat(rejected.getBody())
                .containsEntry("membershipId", membershipId)
                .containsEntry("status", "RECHAZADO");

        var pending = http.exchange(
                "/teams/" + groupId + "/memberships?status=PENDIENTE",
                HttpMethod.GET,
                new HttpEntity<>(headers(coachSession)),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}
        );

        assertThat(pending.getStatusCode().value()).isEqualTo(200);
        assertThat(pending.getBody()).isEmpty();
    }


    @Test
    void anotherCoachCannotReviewPendingMembership() {
        User owner = account(Role.COACH);
        String ownerSession = login(owner);

        var ownerProfile = http.exchange(
                "/users/me",
                HttpMethod.PUT,
                new HttpEntity<>(
                        Map.of(
                                "especialidadPrincipal", "Grafos",
                                "organizacionClub", "Club ICPC",
                                "aniosExperiencia", 4,
                                "presentacion", "Entrenador ICPC"
                        ),
                        headers(ownerSession)
                ),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(ownerProfile.getStatusCode().value()).isEqualTo(200);

        var created = http.exchange(
                "/teams",
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "name", "Grupo Privado",
                                "expectedLevel", "Div3",
                                "maxCapacity", 5,
                                "visibility", "PROTEGIDO"
                        ),
                        headers(ownerSession)
                ),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(created.getStatusCode().value()).isEqualTo(201);
        Integer groupId = (Integer) created.getBody().get("groupId");

        User practitioner = account(Role.PRACTICANTE);
        String practitionerSession = login(practitioner);

        var practitionerProfile = http.exchange(
                "/users/me",
                HttpMethod.PUT,
                new HttpEntity<>(
                        Map.of(
                                "codigoEstudiante",
                                "T" + UUID.randomUUID().toString().substring(0, 8),
                                "carrera", "Ingenieria de Software",
                                "cicloAcademico", 5,
                                "nivelCompetitivo", "INTERMEDIO"
                        ),
                        headers(practitionerSession)
                ),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(practitionerProfile.getStatusCode().value()).isEqualTo(200);

        var joined = http.exchange(
                "/teams/" + groupId + "/join",
                HttpMethod.POST,
                new HttpEntity<>(headers(practitionerSession)),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(joined.getStatusCode().value()).isEqualTo(201);
        Integer membershipId = (Integer) joined.getBody().get("membershipId");

        User otherCoach = account(Role.COACH);
        String otherSession = login(otherCoach);

        var otherProfile = http.exchange(
                "/users/me",
                HttpMethod.PUT,
                new HttpEntity<>(
                        Map.of(
                                "especialidadPrincipal", "Programacion Dinamica",
                                "organizacionClub", "Otro Club",
                                "aniosExperiencia", 2,
                                "presentacion", "Otro entrenador"
                        ),
                        headers(otherSession)
                ),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(otherProfile.getStatusCode().value()).isEqualTo(200);

        var denied = http.exchange(
                "/teams/" + groupId + "/memberships/" + membershipId,
                HttpMethod.PATCH,
                new HttpEntity<>(
                        Map.of("decision", "ACEPTAR"),
                        headers(otherSession)
                ),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(denied.getStatusCode().value()).isEqualTo(403);

        var pending = http.exchange(
                "/teams/" + groupId + "/memberships?status=PENDIENTE",
                HttpMethod.GET,
                new HttpEntity<>(headers(ownerSession)),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}
        );

        assertThat(pending.getStatusCode().value()).isEqualTo(200);
        assertThat(pending.getBody()).hasSize(1);
        assertThat(pending.getBody().get(0))
                .containsEntry("status", "PENDIENTE");
    }


    @Test
    void duplicatePendingRequestReturns409() {
        User coach = account(Role.COACH);
        String coachSession = login(coach);

        var coachProfile = http.exchange(
                "/users/me",
                HttpMethod.PUT,
                new HttpEntity<>(
                        Map.of(
                                "especialidadPrincipal", "Grafos",
                                "organizacionClub", "Club ICPC",
                                "aniosExperiencia", 4,
                                "presentacion", "Entrenador ICPC"
                        ),
                        headers(coachSession)
                ),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(coachProfile.getStatusCode().value()).isEqualTo(200);

        var created = http.exchange(
                "/teams",
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "name", "Grupo Solicitud Duplicada",
                                "expectedLevel", "Div3",
                                "maxCapacity", 5,
                                "visibility", "PROTEGIDO"
                        ),
                        headers(coachSession)
                ),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(created.getStatusCode().value()).isEqualTo(201);
        Integer groupId = (Integer) created.getBody().get("groupId");

        User practitioner = account(Role.PRACTICANTE);
        String practitionerSession = login(practitioner);

        var practitionerProfile = http.exchange(
                "/users/me",
                HttpMethod.PUT,
                new HttpEntity<>(
                        Map.of(
                                "codigoEstudiante",
                                "T" + UUID.randomUUID().toString().substring(0, 8),
                                "carrera", "Ingenieria de Software",
                                "cicloAcademico", 5,
                                "nivelCompetitivo", "INTERMEDIO"
                        ),
                        headers(practitionerSession)
                ),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(practitionerProfile.getStatusCode().value()).isEqualTo(200);

        var first = http.exchange(
                "/teams/" + groupId + "/join",
                HttpMethod.POST,
                new HttpEntity<>(headers(practitionerSession)),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(first.getStatusCode().value()).isEqualTo(201);
        assertThat(first.getBody()).containsEntry("status", "PENDIENTE");

        var duplicate = http.exchange(
                "/teams/" + groupId + "/join",
                HttpMethod.POST,
                new HttpEntity<>(headers(practitionerSession)),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(duplicate.getStatusCode().value()).isEqualTo(409);
        assertThat(duplicate.getBody()).containsKey("message");

        var pending = http.exchange(
                "/teams/" + groupId + "/memberships?status=PENDIENTE",
                HttpMethod.GET,
                new HttpEntity<>(headers(coachSession)),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}
        );

        assertThat(pending.getStatusCode().value()).isEqualTo(200);
        assertThat(pending.getBody()).hasSize(1);
    }


    @Test
    void fullGroupRejectsAnotherPractitioner() {
        User coach = account(Role.COACH);
        String coachSession = login(coach);

        var coachProfile = http.exchange(
                "/users/me",
                HttpMethod.PUT,
                new HttpEntity<>(
                        Map.of(
                                "especialidadPrincipal", "Grafos",
                                "organizacionClub", "Club ICPC",
                                "aniosExperiencia", 4,
                                "presentacion", "Entrenador ICPC"
                        ),
                        headers(coachSession)
                ),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(coachProfile.getStatusCode().value()).isEqualTo(200);

        var created = http.exchange(
                "/teams",
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "name", "Grupo con Cupo Limitado",
                                "expectedLevel", "Div3",
                                "maxCapacity", 1,
                                "visibility", "PUBLICO"
                        ),
                        headers(coachSession)
                ),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(created.getStatusCode().value()).isEqualTo(201);
        Integer groupId = (Integer) created.getBody().get("groupId");

        User firstPractitioner = account(Role.PRACTICANTE);
        String firstSession = login(firstPractitioner);

        var firstProfile = http.exchange(
                "/users/me",
                HttpMethod.PUT,
                new HttpEntity<>(
                        Map.of(
                                "codigoEstudiante",
                                "T" + UUID.randomUUID().toString().substring(0, 8),
                                "carrera", "Ingenieria de Software",
                                "cicloAcademico", 5,
                                "nivelCompetitivo", "INTERMEDIO"
                        ),
                        headers(firstSession)
                ),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(firstProfile.getStatusCode().value()).isEqualTo(200);

        var firstJoin = http.exchange(
                "/teams/" + groupId + "/join",
                HttpMethod.POST,
                new HttpEntity<>(headers(firstSession)),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(firstJoin.getStatusCode().value()).isEqualTo(201);
        assertThat(firstJoin.getBody()).containsEntry("status", "ACTIVO");

        User secondPractitioner = account(Role.PRACTICANTE);
        String secondSession = login(secondPractitioner);

        var secondProfile = http.exchange(
                "/users/me",
                HttpMethod.PUT,
                new HttpEntity<>(
                        Map.of(
                                "codigoEstudiante",
                                "T" + UUID.randomUUID().toString().substring(0, 8),
                                "carrera", "Ingenieria de Software",
                                "cicloAcademico", 5,
                                "nivelCompetitivo", "INTERMEDIO"
                        ),
                        headers(secondSession)
                ),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(secondProfile.getStatusCode().value()).isEqualTo(200);

        var denied = http.exchange(
                "/teams/" + groupId + "/join",
                HttpMethod.POST,
                new HttpEntity<>(headers(secondSession)),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(denied.getStatusCode().value()).isEqualTo(409);
        assertThat(denied.getBody()).containsKey("message");
    }

    // ---- GET /teams/me y datos del postulante

    @Test
    void coachSeesOnlyTheirOwnGroupsWithTheInvitationCodeAndTheMemberCounts() {
        String coachSession = coachSession(account(Role.COACH));
        var open = createGroup(coachSession, "Mis equipos publico", "PUBLICO", 5);
        var closed = createGroup(coachSession, "Mis equipos protegido", "PROTEGIDO", 5);
        createGroup(coachSession(account(Role.COACH)), "Grupo ajeno", "PUBLICO", 5);

        join(practitionerSession(account(Role.PRACTICANTE)), (Integer) open.get("groupId"));
        join(practitionerSession(account(Role.PRACTICANTE)), (Integer) closed.get("groupId"));
        join(practitionerSession(account(Role.PRACTICANTE)), (Integer) closed.get("groupId"));

        var mine = myTeams(coachSession);

        assertThat(mine).hasSize(2);
        assertThat(mine.get(0)).containsEntry("groupId", open.get("groupId"))
                .containsEntry("name", "Mis equipos publico")
                .containsEntry("visibility", "PUBLICO")
                .containsEntry("invitationCode", open.get("invitationCode"))
                .containsEntry("activeMembers", 1).containsEntry("pendingRequests", 0)
                .doesNotContainKey("membership");
        assertThat(mine.get(1)).containsEntry("groupId", closed.get("groupId"))
                .containsEntry("visibility", "PROTEGIDO")
                .containsEntry("invitationCode", closed.get("invitationCode"))
                .containsEntry("activeMembers", 0).containsEntry("pendingRequests", 2);
    }

    @Test
    void coachListsTheActiveMembersOfTheirGroup() {
        String coachSession = coachSession(account(Role.COACH));
        var group = createGroup(coachSession, "Integrantes visibles", "PUBLICO", 5);
        String practitionerSession = practitionerSession(account(Role.PRACTICANTE));
        join(practitionerSession, (Integer) group.get("groupId"));

        var active = requests(coachSession, (Integer) group.get("groupId"), "ACTIVO");

        assertThat(active).hasSize(1);
        assertThat(active.get(0))
                .containsEntry("status", "ACTIVO")
                .containsEntry("groupId", group.get("groupId"));
        assertThat(active.get(0).get("practitioner")).isNotNull();

        // Un estado no admitido sigue respondiendo 400.
        var invalid = http.exchange(
                "/teams/" + group.get("groupId") + "/memberships?status=RETIRADO",
                HttpMethod.GET,
                new HttpEntity<>(headers(coachSession)),
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(invalid.getStatusCode().value()).isEqualTo(400);

        // Un practicante no puede consultar la lista de integrantes.
        var forbidden = http.exchange(
                "/teams/" + group.get("groupId") + "/memberships?status=ACTIVO",
                HttpMethod.GET,
                new HttpEntity<>(headers(practitionerSession)),
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(forbidden.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void coachExpelsAnActiveMemberAndThatMemberCannotRejoin() {
        String coachSession = coachSession(account(Role.COACH));
        var group = createGroup(coachSession, "Expulsion US06", "PUBLICO", 5);
        String practitionerSession = practitionerSession(account(Role.PRACTICANTE));
        join(practitionerSession, (Integer) group.get("groupId"));

        var active = requests(coachSession, (Integer) group.get("groupId"), "ACTIVO");
        Object membershipId = active.get(0).get("membershipId");

        var expelled = http.exchange(
                "/teams/" + group.get("groupId") + "/memberships/" + membershipId,
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("decision", "EXPULSAR"), headers(coachSession)),
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(expelled.getStatusCode().value()).isEqualTo(200);
        assertThat(expelled.getBody()).containsEntry("status", "EXPULSADO");
        assertThat(requests(coachSession, (Integer) group.get("groupId"), "ACTIVO")).isEmpty();

        // El cupo vuelve a estar libre.
        var mine = myTeams(coachSession);
        assertThat(mine.get(0)).containsEntry("activeMembers", 0);

        // Quien fue expulsado no puede volver a postular.
        var rejoin = http.exchange(
                "/teams/" + group.get("groupId") + "/join",
                HttpMethod.POST,
                new HttpEntity<>(headers(practitionerSession)),
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(rejoin.getStatusCode().value()).isEqualTo(403);

        // No se puede expulsar dos veces.
        var again = http.exchange(
                "/teams/" + group.get("groupId") + "/memberships/" + membershipId,
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("decision", "EXPULSAR"), headers(coachSession)),
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(again.getStatusCode().value()).isEqualTo(409);

        // El coach sí puede readmitir al expulsado.
        var expelledList = requests(coachSession, (Integer) group.get("groupId"), "EXPULSADO");
        assertThat(expelledList).hasSize(1);
        assertThat(expelledList.get(0)).containsEntry("status", "EXPULSADO");

        var readmitted = http.exchange(
                "/teams/" + group.get("groupId") + "/memberships/" + membershipId,
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("decision", "READMITIR"), headers(coachSession)),
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(readmitted.getStatusCode().value()).isEqualTo(200);
        assertThat(readmitted.getBody()).containsEntry("status", "ACTIVO");
        assertThat(requests(coachSession, (Integer) group.get("groupId"), "ACTIVO")).hasSize(1);
        assertThat(myTeams(coachSession).get(0)).containsEntry("activeMembers", 1);

        // Ya no está expulsado: una segunda readmisión responde 409.
        var readmitAgain = http.exchange(
                "/teams/" + group.get("groupId") + "/memberships/" + membershipId,
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("decision", "READMITIR"), headers(coachSession)),
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(readmitAgain.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void practitionerSeesTheStatusOfEachOfTheirRequestsAndNeverTheCoachFields() {
        String coachSession = coachSession(account(Role.COACH));
        var open = createGroup(coachSession, "Practicante publico", "PUBLICO", 5);
        var closed = createGroup(coachSession, "Practicante protegido", "PROTEGIDO", 5);
        String session = practitionerSession(account(Role.PRACTICANTE));
        join(session, (Integer) open.get("groupId"));
        join(session, (Integer) closed.get("groupId"));

        var before = myTeams(session);

        assertThat(before).hasSize(2);
        assertThat(before.get(0)).containsEntry("groupId", open.get("groupId"));
        assertThat(membershipOf(before.get(0))).containsEntry("status", "ACTIVO").containsEntry("teamRole", "MIEMBRO");
        assertThat(membershipOf(before.get(1))).containsEntry("status", "PENDIENTE");
        assertThat(before).allSatisfy(team -> assertThat(team)
                .doesNotContainKeys("invitationCode", "activeMembers", "pendingRequests"));

        // El coach rechaza la solicitud y el practicante lo ve sin que nadie se lo notifique.
        Object membershipId = membershipOf(before.get(1)).get("membershipId");
        var review = http.exchange("/teams/" + closed.get("groupId") + "/memberships/" + membershipId,
                HttpMethod.PATCH, new HttpEntity<>(Map.of("decision", "RECHAZAR"), headers(coachSession)),
                new ParameterizedTypeReference<Map<String, Object>>() {});
        assertThat(review.getStatusCode().value()).isEqualTo(200);
        assertThat(membershipOf(myTeams(session).get(1))).containsEntry("status", "RECHAZADO");

        // Quien todavía no pidió ingreso a ningún grupo recibe una lista vacía.
        assertThat(myTeams(practitionerSession(account(Role.PRACTICANTE)))).isEmpty();
    }

    @Test
    void myTeamsRequiresAToken() {
        var response = http.exchange("/teams/me", HttpMethod.GET, new HttpEntity<>(headers(null)),
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void coachSeesTheApplicantsProfileInThePendingRequestsOldestFirst() {
        String coachSession = coachSession(account(Role.COACH));
        Integer groupId = (Integer) createGroup(coachSession, "Solicitudes con perfil", "PROTEGIDO", 5).get("groupId");
        User first = account(Role.PRACTICANTE);
        User second = account(Role.PRACTICANTE);
        join(practitionerSession(first), groupId);
        join(practitionerSession(second), groupId);

        var pending = http.exchange("/teams/" + groupId + "/memberships?status=PENDIENTE", HttpMethod.GET,
                new HttpEntity<>(headers(coachSession)),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {});

        assertThat(pending.getStatusCode().value()).isEqualTo(200);
        assertThat(pending.getBody()).hasSize(2);
        assertThat(pending.getBody()).extracting(item -> item.get("practitionerId"))
                .containsExactly(first.getId(), second.getId());
        Map<?, ?> applicant = (Map<?, ?>) pending.getBody().get(0).get("practitioner");
        assertThat(applicant.get("fullName")).isEqualTo("Usuario de Prueba");
        assertThat(applicant.get("studentCode")).asString().isNotBlank();
        assertThat(applicant.get("career")).isEqualTo("Ingenieria de Software");
        assertThat(applicant.get("academicCycle")).isEqualTo(5);
        assertThat(applicant.get("competitiveLevel")).isEqualTo("INTERMEDIO");
        assertThat(applicant.get("codeforcesHandle")).isNull();
        assertThat(pending.getBody().toString()).doesNotContain(first.getEmail()).doesNotContain(second.getEmail());
    }

    @Test
    void practitionerJoinsAProtectedGroupWithOnlyTheInvitationCode() {
        String coachSession = coachSession(account(Role.COACH));
        Map<String, Object> group = createGroup(coachSession, "Grupo por codigo", "PROTEGIDO", 5);
        Integer groupId = (Integer) group.get("groupId");
        String code = (String) group.get("invitationCode");

        // El coach no puede ingresar por código.
        assertThat(joinByCode(coachSession, code).getStatusCode().value()).isEqualTo(403);

        String practitionerSession = practitionerSession(account(Role.PRACTICANTE));

        var unknown = joinByCode(practitionerSession, "ZZZZZZZZZZZZ");
        assertThat(unknown.getStatusCode().value()).isEqualTo(404);
        assertThat(unknown.getBody()).containsEntry("message", "El código de invitación no es válido");

        // El código en minúsculas y con espacios también vale.
        var joined = joinByCode(practitionerSession, "  " + code.toLowerCase() + " ");
        assertThat(joined.getStatusCode().value()).isEqualTo(201);
        assertThat(joined.getBody())
                .containsEntry("status", "ACTIVO")
                .containsEntry("groupId", groupId);

        assertThat(joinByCode(practitionerSession, code).getStatusCode().value()).isEqualTo(409);

        // El ingreso por id sin código sigue generando una solicitud pendiente.
        String other = practitionerSession(account(Role.PRACTICANTE));
        assertThat(join(other, groupId)).containsEntry("status", "PENDIENTE");
    }

    @Test
    void draftIsHiddenUntilTheCoachPublishesIt() {
        String coachSession = coachSession(account(Role.COACH));

        var created = http.exchange("/teams", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "Borrador de grafos", "draft", true, "visibility", "PROTEGIDO"),
                        headers(coachSession)),
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(created.getStatusCode().value()).isEqualTo(201);
        assertThat(created.getBody())
                .containsEntry("status", "BORRADOR")
                .containsEntry("message", "Borrador del grupo guardado correctamente");
        Integer groupId = (Integer) created.getBody().get("groupId");

        assertThat(myTeams(coachSession))
                .anySatisfy(team -> assertThat(team)
                        .containsEntry("groupId", groupId)
                        .containsEntry("status", "BORRADOR"));

        // Un borrador no aparece en el listado público ni admite solicitudes de ingreso.
        assertThat(availableGroups(coachSession)).noneSatisfy(group -> assertThat(group).containsEntry("groupId", groupId));

        String practitionerSession = practitionerSession(account(Role.PRACTICANTE));
        var joinDraft = http.exchange("/teams/" + groupId + "/join", HttpMethod.POST,
                new HttpEntity<>(headers(practitionerSession)),
                new ParameterizedTypeReference<Map<String, Object>>() {});
        assertThat(joinDraft.getStatusCode().value()).isEqualTo(409);

        // Publicar exige completar nivel y cupo; luego el grupo sí se lista.
        var incomplete = http.exchange("/teams/" + groupId + "/publish", HttpMethod.POST,
                new HttpEntity<>(headers(coachSession)),
                new ParameterizedTypeReference<Map<String, Object>>() {});
        assertThat(incomplete.getStatusCode().value()).isEqualTo(400);

        var updated = http.exchange("/teams/" + groupId + "/draft", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("expectedLevel", "Div3", "maxCapacity", 5), headers(coachSession)),
                new ParameterizedTypeReference<Map<String, Object>>() {});
        assertThat(updated.getStatusCode().value()).isEqualTo(200);
        assertThat(updated.getBody()).containsEntry("name", "Borrador de grafos");

        var published = http.exchange("/teams/" + groupId + "/publish", HttpMethod.POST,
                new HttpEntity<>(headers(coachSession)),
                new ParameterizedTypeReference<Map<String, Object>>() {});
        assertThat(published.getStatusCode().value()).isEqualTo(200);
        assertThat(published.getBody()).containsEntry("status", "ACTIVO");

        assertThat(availableGroups(coachSession)).anySatisfy(group -> assertThat(group).containsEntry("groupId", groupId));
    }

    @Test
    void coachRequestsClarificationAndTheApplicantAnswersBeforeTheDecision() {
        String coachSession = coachSession(account(Role.COACH));
        Integer groupId = (Integer) createGroup(coachSession, "Grupo con aclaracion", "PROTEGIDO", 5).get("groupId");
        String practitionerSession = practitionerSession(account(Role.PRACTICANTE));

        var joined = join(practitionerSession, groupId);
        Integer membershipId = (Integer) joined.get("membershipId");
        String question = "¿Puedes adjuntar el enlace de tu perfil de Codeforces?";

        var asked = http.exchange("/teams/" + groupId + "/memberships/" + membershipId, HttpMethod.PATCH,
                new HttpEntity<>(Map.of("decision", "SOLICITAR_ACLARACION", "message", question),
                        headers(coachSession)),
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(asked.getStatusCode().value()).isEqualTo(200);
        assertThat(asked.getBody())
                .containsEntry("status", "ACLARACION_SOLICITADA")
                .containsEntry("clarificationRequest", question);

        // Ya no aparece entre las pendientes, pero sí en su propio estado.
        assertThat(requests(coachSession, groupId, "PENDIENTE")).isEmpty();
        assertThat(requests(coachSession, groupId, "ACLARACION_SOLICITADA")).hasSize(1);

        // El coach no puede decidir hasta que el postulante responda.
        var earlyDecision = http.exchange("/teams/" + groupId + "/memberships/" + membershipId, HttpMethod.PATCH,
                new HttpEntity<>(Map.of("decision", "ACEPTAR"), headers(coachSession)),
                new ParameterizedTypeReference<Map<String, Object>>() {});
        assertThat(earlyDecision.getStatusCode().value()).isEqualTo(409);

        // El postulante ve la pregunta en /teams/me.
        var mine = myTeams(practitionerSession);
        assertThat(membershipOf(mine.get(0)))
                .containsEntry("status", "ACLARACION_SOLICITADA");
        Map<?, ?> clarification = (Map<?, ?>) membershipOf(mine.get(0)).get("clarification");
        assertThat(clarification.get("request")).isEqualTo(question);

        var answered = http.exchange("/teams/" + groupId + "/memberships/" + membershipId + "/clarification",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("message", "Mi usuario es practicante_2026"), headers(practitionerSession)),
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(answered.getStatusCode().value()).isEqualTo(200);
        assertThat(answered.getBody())
                .containsEntry("status", "PENDIENTE")
                .containsEntry("clarificationResponse", "Mi usuario es practicante_2026");

        assertThat(requests(coachSession, groupId, "PENDIENTE")).hasSize(1);

        var accepted = http.exchange("/teams/" + groupId + "/memberships/" + membershipId, HttpMethod.PATCH,
                new HttpEntity<>(Map.of("decision", "ACEPTAR"), headers(coachSession)),
                new ParameterizedTypeReference<Map<String, Object>>() {});
        assertThat(accepted.getStatusCode().value()).isEqualTo(200);
        assertThat(accepted.getBody()).containsEntry("status", "ACTIVO");
    }

    @Test
    void anotherPractitionerCannotAnswerTheClarification() {
        String coachSession = coachSession(account(Role.COACH));
        Integer groupId = (Integer) createGroup(coachSession, "Grupo aclaracion ajena", "PROTEGIDO", 5).get("groupId");
        String ownerSession = practitionerSession(account(Role.PRACTICANTE));
        Integer membershipId = (Integer) join(ownerSession, groupId).get("membershipId");

        http.exchange("/teams/" + groupId + "/memberships/" + membershipId, HttpMethod.PATCH,
                new HttpEntity<>(Map.of("decision", "SOLICITAR_ACLARACION", "message", "¿Datos?"),
                        headers(coachSession)),
                new ParameterizedTypeReference<Map<String, Object>>() {});

        String intruderSession = practitionerSession(account(Role.PRACTICANTE));
        var denied = http.exchange("/teams/" + groupId + "/memberships/" + membershipId + "/clarification",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("message", "Respuesta ajena"), headers(intruderSession)),
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(denied.getStatusCode().value()).isEqualTo(403);
    }

    private List<Map<String, Object>> availableGroups(String session) {
        var response = http.exchange("/teams", HttpMethod.GET, new HttpEntity<>(headers(session)),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return response.getBody();
    }

    private List<Map<String, Object>> requests(String session, Integer groupId, String status) {
        var response = http.exchange("/teams/" + groupId + "/memberships?status=" + status, HttpMethod.GET,
                new HttpEntity<>(headers(session)),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return response.getBody();
    }

    private User account(Role role) {
        User user = new User();

        user.setFullName("Usuario de Prueba");
        user.setEmail(
                "test.teams."
                        + UUID.randomUUID()
                        + "@gmail.com"
        );
        user.setPasswordHash(encoder.encode(PASSWORD));
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVO);
        user.setCreatedAt(OffsetDateTime.now());

        return users.saveAndFlush(user);
    }

    private String login(User user) {
        var response = http.exchange(
                "/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "email", user.getEmail(),
                                "password", PASSWORD
                        ),
                        headers(null)
                ),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertThat(response.getStatusCode().value())
                .isEqualTo(200);

        Object token = response.getBody().get("token");

        assertThat(token).isInstanceOf(String.class);

        return (String) token;
    }

    /** Inicia sesión con una cuenta de coach y le registra su perfil; devuelve su token. */
    private String coachSession(User coach) {
        String session = login(coach);
        var profile = http.exchange("/users/me", HttpMethod.PUT,
                new HttpEntity<>(Map.of("especialidadPrincipal", "Grafos", "aniosExperiencia", 4), headers(session)),
                new ParameterizedTypeReference<Map<String, Object>>() {});
        assertThat(profile.getStatusCode().value()).isEqualTo(200);
        return session;
    }

    /** Inicia sesión con una cuenta de practicante y le registra su perfil; devuelve su token. */
    private String practitionerSession(User practitioner) {
        String session = login(practitioner);
        var profile = http.exchange("/users/me", HttpMethod.PUT,
                new HttpEntity<>(Map.of("codigoEstudiante", "T" + UUID.randomUUID().toString().substring(0, 8),
                        "carrera", "Ingenieria de Software", "cicloAcademico", 5, "nivelCompetitivo", "INTERMEDIO"),
                        headers(session)),
                new ParameterizedTypeReference<Map<String, Object>>() {});
        assertThat(profile.getStatusCode().value()).isEqualTo(200);
        return session;
    }

    private Map<String, Object> createGroup(String coachSession, String name, String visibility, int capacity) {
        var created = http.exchange("/teams", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", name, "expectedLevel", "Div3", "maxCapacity", capacity,
                        "visibility", visibility), headers(coachSession)),
                new ParameterizedTypeReference<Map<String, Object>>() {});
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        return created.getBody();
    }

    private Map<String, Object> join(String session, Integer groupId) {
        var joined = http.exchange("/teams/" + groupId + "/join", HttpMethod.POST,
                new HttpEntity<>(headers(session)), new ParameterizedTypeReference<Map<String, Object>>() {});
        assertThat(joined.getStatusCode().value()).isEqualTo(201);
        return joined.getBody();
    }

    private org.springframework.http.ResponseEntity<Map<String, Object>> joinByCode(String session, String code) {
        return http.exchange("/teams/join-by-code", HttpMethod.POST,
                new HttpEntity<>(Map.of("invitationCode", code), headers(session)),
                new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    private List<Map<String, Object>> myTeams(String session) {
        var response = http.exchange("/teams/me", HttpMethod.GET, new HttpEntity<>(headers(session)),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return response.getBody();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> membershipOf(Map<String, Object> team) {
        return (Map<String, Object>) team.get("membership");
    }

    private HttpHeaders headers(String session) {
        HttpHeaders headers = new HttpHeaders();

        headers.setContentType(MediaType.APPLICATION_JSON);

        if (session != null) {
            headers.setBearerAuth(session);
        }

        return headers;
    }
}

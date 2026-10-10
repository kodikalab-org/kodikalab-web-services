
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

    private HttpHeaders headers(String session) {
        HttpHeaders headers = new HttpHeaders();

        headers.setContentType(MediaType.APPLICATION_JSON);

        if (session != null) {
            headers.setBearerAuth(session);
        }

        return headers;
    }
}

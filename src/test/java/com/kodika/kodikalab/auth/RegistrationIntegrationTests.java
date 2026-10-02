package com.kodika.kodikalab.auth;

import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserRepository;
import com.kodika.kodikalab.users.UserStatus;
import com.kodika.kodikalab.users.UserService;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.*;

/** Real HTTP and PostgreSQL, opt-in, with DDL restricted to a disposable schema. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "REGISTRATION_TEST_DB_URL", matches = ".+")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RegistrationIntegrationTests {
    private static final String SCHEMA = "registration_test_" + UUID.randomUUID().toString().replace("-", "");
    @Autowired TestRestTemplate http;
    @Autowired UserRepository repository;
    @Autowired PasswordEncoder encoder;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired RequestMappingHandlerMapping mappings;

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(System.getenv("REGISTRATION_TEST_DB_URL"),
                System.getenv().getOrDefault("REGISTRATION_TEST_DB_USER", "postgres"),
                System.getenv().getOrDefault("REGISTRATION_TEST_DB_PASSWORD", ""));
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) throws SQLException {
        try (Connection connection = connect(); var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + SCHEMA);
        }
        registry.add("spring.datasource.url", () -> System.getenv("REGISTRATION_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("REGISTRATION_TEST_DB_USER", "postgres"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("REGISTRATION_TEST_DB_PASSWORD", ""));
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA);
        registry.add("spring.jpa.show-sql", () -> "false");
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        try (Connection connection = connect(); var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }

    private String email() {
        return "registration." + UUID.randomUUID() + "@upc.edu.pe";
    }

    private Map<String, String> request(String email, String password, String role) {
        return Map.of("firstName", "Matias", "lastName", "Del Castillo", "email", email,
                "password", password, "role", role);
    }

    private ResponseEntity<Map<String, Object>> register(Map<String, String> request) {
        // TestRestTemplate already includes server.servlet.context-path in its root URI.
        return http.exchange("/auth/register", HttpMethod.POST, new HttpEntity<>(request),
                new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    @Test
    void registrationPersistsOnlyHashedPasswordAndActiveRole() {
        String email = email();
        var response = register(request(email.toUpperCase(), "Password123", "PRACTITIONER"));
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).containsEntry("message", "Registro exitoso").containsEntry("email", email)
                .containsEntry("role", "PRACTITIONER").doesNotContainKeys("password", "passwordHash", "token");
        User saved = repository.findByEmail(email).orElseThrow();
        assertThat(saved.getFirstName()).isEqualTo("Matias");
        assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getPasswordHash()).isNotEqualTo("Password123");
        assertThat(encoder.matches("Password123", saved.getPasswordHash())).isTrue();
    }

    @Test
    void duplicateAndCaseVariantEmailDoNotCreateSecondUser() {
        String email = email();
        assertThat(register(request(email, "Password123", "COACH")).getStatusCode().value()).isEqualTo(201);
        long count = repository.count();
        var response = register(request(email.toUpperCase(), "Password123", "COACH"));
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).containsEntry("message", UserService.EMAIL_ALREADY_REGISTERED);
        assertThat(repository.count()).isEqualTo(count);
    }

    @Test
    void weakPasswordDoesNotPersistAccount() {
        String email = email();
        var response = register(request(email, "12345", "PRACTITIONER"));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).containsEntry("message",
                "La contraseña debe contener al menos 8 caracteres, una mayúscula y un número");
        assertThat(repository.existsByEmail(email)).isFalse();
    }

    @Test
    void bcryptByteLimitDoesNotPersistAccount() {
        String email = email();
        var response = register(request(email, "A1" + "ñ".repeat(36), "PRACTITIONER"));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(repository.existsByEmail(email)).isFalse();
    }

    @Test
    void invalidEmailAndRoleDoNotPersistAccount() {
        String email = email();
        assertThat(register(request(email, "Password123", "ROOT")).getStatusCode().value()).isEqualTo(400);
        assertThat(repository.existsByEmail(email)).isFalse();
        assertThat(register(request("not-an-email", "Password123", "PRACTITIONER")).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void concurrentRequestsCreateExactlyOneAccount() throws Exception {
        String email = email();
        Callable<Integer> task = () -> register(request(email, "Password123", "PRACTITIONER")).getStatusCode().value();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = executor.invokeAll(List.of(task, task));
            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(repository.findAll().stream().filter(user -> email.equals(user.getEmail())).count()).isEqualTo(1);
    }

    @Test
    void legacyFilesCoexistWithoutDuplicateMappingsOrEntities() {
        long registrationRoutes = mappings.getHandlerMethods().keySet().stream()
                .filter(mapping -> mapping.getPatternValues().contains("/auth/register")).count();
        long loginRoutes = mappings.getHandlerMethods().keySet().stream()
                .filter(mapping -> mapping.getPatternValues().contains("/auth/login")).count();
        assertThat(registrationRoutes).isEqualTo(1);
        assertThat(loginRoutes).isEqualTo(1);
        assertThat(entityManagerFactory.getMetamodel().getEntities().stream()
                .filter(entity -> entity.getJavaType().getSimpleName().equals("User"))
                .map(entity -> entity.getJavaType().getName()).toList())
                .containsExactly(User.class.getName());
    }
}

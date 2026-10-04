package com.kodika.kodikalab.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kodika.kodikalab.auth.dto.AuthResponse;
import com.kodika.kodikalab.auth.dto.RegisterRequest;
import com.kodika.kodikalab.common.exception.ConflictException;
import com.kodika.kodikalab.users.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerTests {
    private static final String VALID = """
            {"firstName":"Matias","lastName":"Del Castillo","email":"matias@upc.edu.pe",
             "password":"Password123","role":"PRACTITIONER"}
            """;
    AuthService service;
    MockMvc mvc;
    LocalValidatorFactoryBean validator;
    ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = mock(AuthService.class);
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(service))
                .setControllerAdvice(new AuthExceptionHandler()).setValidator(validator).build();
    }

    @AfterEach
    void close() {
        validator.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PRACTITIONER", "COACH", "ADMIN"})
    void acceptsEveryDefinedRole(String role) throws Exception {
        when(service.register(any())).thenAnswer(invocation -> {
            RegisterRequest request = invocation.getArgument(0);
            return new AuthResponse("Registro exitoso", request.email(), request.role());
        });
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(VALID.replace("PRACTITIONER", role)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.message").value("Registro exitoso"))
                .andExpect(jsonPath("$.role").value(role)).andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist()).andExpect(jsonPath("$.token").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"firstName", "lastName", "email", "password", "role"})
    void requiredFieldsCannotBeMissing(String field) throws Exception {
        ObjectNode request = (ObjectNode) mapper.readTree(VALID);
        request.remove(field);
        assertInvalid(request.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"firstName", "lastName", "email", "password", "role"})
    void requiredFieldsCannotBeNull(String field) throws Exception {
        ObjectNode request = (ObjectNode) mapper.readTree(VALID);
        request.putNull(field);
        assertInvalid(request.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"firstName", "lastName", "email", "password"})
    void requiredFieldsCannotBeBlank(String field) throws Exception {
        ObjectNode request = (ObjectNode) mapper.readTree(VALID);
        request.put(field, "   ");
        assertInvalid(request.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "password123", "Password", "Pass1"})
    void weakPasswordReturnsExactAcceptanceMessage(String password) throws Exception {
        ObjectNode request = (ObjectNode) mapper.readTree(VALID);
        request.put("password", password);
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(
                        "La contraseña debe contener al menos 8 caracteres, una mayúscula y un número"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"firstName", "lastName", "email"})
    void respectsSqlColumnLengths(String field) throws Exception {
        ObjectNode request = (ObjectNode) mapper.readTree(VALID);
        request.put(field, field.equals("email") ? "a".repeat(256) + "@upc.edu.pe" : "a".repeat(81));
        assertInvalid(request.toString());
    }

    @Test
    void rejectsInvalidEmail() throws Exception {
        assertInvalid(VALID.replace("matias@upc.edu.pe", "not-an-email"));
    }

    @Test
    void rejectsUndefinedRole() throws Exception {
        assertInvalid(VALID.replace("PRACTITIONER", "ROOT"));
    }

    @Test
    void rejectsNumericRoleInsteadOfAcceptingEnumOrdinal() throws Exception {
        assertInvalid(VALID.replace("\"PRACTITIONER\"", "0"));
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        assertInvalid("{broken}");
    }

    @Test
    void duplicateEmailReturnsExactAcceptanceMessage() throws Exception {
        when(service.register(any())).thenThrow(new ConflictException(UserService.EMAIL_ALREADY_REGISTERED));
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(UserService.EMAIL_ALREADY_REGISTERED));
    }

    private void assertInvalid(String request) throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isString())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Password123"))));
        verifyNoInteractions(service);
    }
}

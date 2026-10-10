package com.kodika.kodikalab.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kodika.kodikalab.auth.dto.AuthResponse;
import com.kodika.kodikalab.common.exception.UnauthorizedException;
import com.kodika.kodikalab.users.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LoginControllerTests {
    private static final String VALID = """
            {"email":"test@gmail.com","password":"Password123"}
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
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(service, mock(AccountRecoveryService.class)))
                .setControllerAdvice(new AuthExceptionHandler()).setValidator(validator).build();
    }

    @AfterEach
    void close() {
        validator.close();
    }

    @Test
    void loginReturns200WithTokenAndStoredRole() throws Exception {
        when(service.login(any())).thenReturn(new AuthResponse("Inicio de sesión exitoso", "test@gmail.com",
                Role.PRACTICANTE, "token.firmado.jwt", "Bearer", 3600L));
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("Inicio de sesión exitoso"))
                .andExpect(jsonPath("$.role").value("PRACTICANTE"))
                .andExpect(jsonPath("$.token").value("token.firmado.jwt"))
                .andExpect(jsonPath("$.tokenType").value("Bearer")).andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.password").doesNotExist()).andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void clientCannotSelectAnElevatedRoleAtLogin() throws Exception {
        when(service.login(any())).thenReturn(new AuthResponse("Inicio de sesión exitoso", "test@gmail.com", Role.PRACTICANTE));
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(VALID.replace("}", ",\"role\":\"ADMIN\"}")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("PRACTICANTE"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"email", "password"})
    void missingFieldDoesNotAuthenticate(String field) throws Exception {
        ObjectNode request = (ObjectNode) mapper.readTree(VALID);
        request.remove(field);
        assertInvalid(request.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"email", "password"})
    void nullFieldDoesNotAuthenticate(String field) throws Exception {
        ObjectNode request = (ObjectNode) mapper.readTree(VALID);
        request.putNull(field);
        assertInvalid(request.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"email", "password"})
    void blankFieldDoesNotAuthenticate(String field) throws Exception {
        ObjectNode request = (ObjectNode) mapper.readTree(VALID);
        request.put(field, "   ");
        assertInvalid(request.toString());
    }

    @Test
    void malformedEmailDoesNotAuthenticate() throws Exception {
        assertInvalid(VALID.replace("test@gmail.com", "not-an-email"));
    }

    @Test
    void emailOver100DoesNotAuthenticate() throws Exception {
        ObjectNode request = (ObjectNode) mapper.readTree(VALID);
        request.put("email", "a".repeat(60) + "@" + "b".repeat(30) + ".gmail.com");
        assertInvalid(request.toString());
    }

    @Test
    void malformedJsonDoesNotAuthenticate() throws Exception {
        assertInvalid("{broken}");
    }

    @Test
    void badCredentialsReturnGeneric401WithoutToken() throws Exception {
        when(service.login(any())).thenThrow(new UnauthorizedException("Credenciales inválidas"));
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Credenciales inválidas"))
                .andExpect(jsonPath("$.email").doesNotExist()).andExpect(jsonPath("$.role").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist()).andExpect(jsonPath("$.token").doesNotExist());
    }

    private void assertInvalid(String request) throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isString())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Password123"))));
        verifyNoInteractions(service);
    }
}

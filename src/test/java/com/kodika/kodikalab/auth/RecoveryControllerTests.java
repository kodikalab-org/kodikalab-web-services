package com.kodika.kodikalab.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kodika.kodikalab.auth.dto.RecoveryResponse;
import com.kodika.kodikalab.common.exception.BadRequestException;
import com.kodika.kodikalab.common.exception.UnauthorizedException;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecoveryControllerTests {
    private static final String VALID = """
            {"email":"test@gmail.com","recoveryCode":"ABCD-EFGH-IJKL-MNOP-QRST-UVWX","newPassword":"Nueva1234"}
            """;
    private static final String CODE = "AAAA-BBBB-CCCC-DDDD-EEEE-FFFF";
    AccountRecoveryService recovery;
    MockMvc mvc;
    LocalValidatorFactoryBean validator;
    ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        recovery = mock(AccountRecoveryService.class);
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(mock(AuthService.class), recovery))
                .setControllerAdvice(new AuthExceptionHandler()).setValidator(validator).build();
    }

    @AfterEach
    void close() {
        validator.close();
    }

    @Test
    void recoveryReturns200WithTheNewCodeAndNoSecrets() throws Exception {
        when(recovery.recover(any())).thenReturn(new RecoveryResponse("Contraseña actualizada", CODE));
        mvc.perform(post("/auth/recovery").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("Contraseña actualizada"))
                .andExpect(jsonPath("$.recoveryCode").value(CODE))
                .andExpect(jsonPath("$.newPassword").doesNotExist()).andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"email", "recoveryCode", "newPassword"})
    void missingBlankOrNullFieldsAreRejectedBeforeTheService(String field) throws Exception {
        for (String mutation : new String[]{"remove", "null", "blank"}) {
            ObjectNode body = (ObjectNode) mapper.readTree(VALID);
            switch (mutation) {
                case "remove" -> body.remove(field);
                case "null" -> body.putNull(field);
                default -> body.put(field, "   ");
            }
            assertInvalid(body.toString());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "password123", "Password", "Ab1"})
    void weakNewPasswordIsRejectedWithThePolicyMessage(String password) throws Exception {
        ObjectNode body = (ObjectNode) mapper.readTree(VALID);
        body.put("newPassword", password);
        mvc.perform(post("/auth/recovery").contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La contraseña debe contener al menos 8 caracteres, una mayúscula y un número"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(":\"" + password + "\""))));
        verifyNoInteractions(recovery);
    }

    @Test
    void malformedEmailAndJsonAreRejected() throws Exception {
        assertInvalid(VALID.replace("test@gmail.com", "no-es-un-correo"));
        assertInvalid("{roto}");
    }

    @Test
    void verificationFailureIsAGeneric401() throws Exception {
        when(recovery.recover(any())).thenThrow(new UnauthorizedException("Datos de recuperación inválidos"));
        mvc.perform(post("/auth/recovery").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Datos de recuperación inválidos"))
                .andExpect(jsonPath("$.recoveryCode").doesNotExist()).andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void serviceValidationErrorsAreA400() throws Exception {
        when(recovery.recover(any())).thenThrow(new BadRequestException("La contraseña no debe superar 72 bytes en UTF-8"));
        mvc.perform(post("/auth/recovery").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La contraseña no debe superar 72 bytes en UTF-8"));
    }

    @Test
    void recoveryCodeEndpointReturnsTheCurrentCode() throws Exception {
        when(recovery.showRecoveryCode(any())).thenReturn(new RecoveryResponse("Código vigente", CODE));
        mvc.perform(post("/auth/recovery-code").contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"Password123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recoveryCode").value(CODE))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void recoveryCodeEndpointRejectsBlankPasswordAndWrongPassword() throws Exception {
        mvc.perform(post("/auth/recovery-code").contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/auth/recovery-code").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(recovery);
        when(recovery.showRecoveryCode(any())).thenThrow(new UnauthorizedException("Credenciales inválidas"));
        mvc.perform(post("/auth/recovery-code").contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"Wrong1234\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Credenciales inválidas"))
                .andExpect(jsonPath("$.recoveryCode").doesNotExist());
    }

    private void assertInvalid(String request) throws Exception {
        mvc.perform(post("/auth/recovery").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isString())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Nueva1234"))));
        verifyNoInteractions(recovery);
    }
}

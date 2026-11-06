package com.kodika.kodikalab.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kodika.kodikalab.auth.dto.PasswordResetConfirmRequest;
import com.kodika.kodikalab.auth.dto.PasswordResetRequest;
import com.kodika.kodikalab.auth.dto.PasswordResetResponse;
import com.kodika.kodikalab.auth.dto.PasswordResetVerifyRequest;
import com.kodika.kodikalab.common.exception.BadRequestException;
import com.kodika.kodikalab.common.exception.ServiceUnavailableException;
import com.kodika.kodikalab.common.exception.TooManyRequestsException;
import com.kodika.kodikalab.common.exception.UnauthorizedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PasswordResetControllerTests {
    private static final String REQUEST = "{\"email\":\"test@gmail.com\"}";
    private static final String VERIFY = "{\"email\":\"test@gmail.com\",\"code\":\"123456\"}";
    private static final String CONFIRM =
            "{\"email\":\"test@gmail.com\",\"code\":\"123456\",\"newPassword\":\"Nueva1234\"}";

    PasswordResetService service;
    MockMvc mvc;
    LocalValidatorFactoryBean validator;
    ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = mock(PasswordResetService.class);
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new PasswordResetController(service))
                .setControllerAdvice(new AuthExceptionHandler()).setValidator(validator).build();
    }

    @AfterEach
    void close() {
        validator.close();
    }

    private org.springframework.test.web.servlet.ResultActions call(String path, String body) throws Exception {
        return mvc.perform(post("/auth/password-reset/" + path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    // ---- paso 1

    @Test
    void requestReturns200WithAGenericMessageAndNoCode() throws Exception {
        when(service.requestCode(any())).thenReturn(new PasswordResetResponse("Si el correo está registrado, enviamos un código"));
        call("request", REQUEST).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Si el correo está registrado, enviamos un código"))
                .andExpect(jsonPath("$.code").doesNotExist()).andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void requestNormalizesTheEmail() throws Exception {
        when(service.requestCode(any())).thenReturn(new PasswordResetResponse("ok"));
        call("request", "{\"email\":\"  TEST@Gmail.COM \"}").andExpect(status().isOk());
        var captor = ArgumentCaptor.forClass(PasswordResetRequest.class);
        verify(service).requestCode(captor.capture());
        assertThat(captor.getValue().email()).isEqualTo("test@gmail.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"email\":null}", "{\"email\":\"   \"}", "{\"email\":\"no-es-un-correo\"}", "{roto}"})
    void requestRejectsMissingBlankOrMalformedEmail(String body) throws Exception {
        call("request", body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isString());
        verifyNoInteractions(service);
    }

    @Test
    void requestMapsTheRateLimitTo429AndMissingMailConfigTo503() throws Exception {
        when(service.requestCode(any())).thenThrow(new TooManyRequestsException("Espera un minuto antes de pedir otro código"));
        call("request", REQUEST).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Espera un minuto antes de pedir otro código"));
        reset(service);
        when(service.requestCode(any())).thenThrow(new ServiceUnavailableException("El envío de correos no está configurado"));
        call("request", REQUEST).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("El envío de correos no está configurado"));
    }

    // ---- paso 2

    @Test
    void verifyReturns200() throws Exception {
        when(service.verifyCode(any())).thenReturn(new PasswordResetResponse("Código verificado"));
        call("verify", VERIFY).andExpect(status().isOk()).andExpect(jsonPath("$.message").value("Código verificado"));
    }

    @Test
    void verifyIgnoresSpacesInsideTheCode() throws Exception {
        when(service.verifyCode(any())).thenReturn(new PasswordResetResponse("ok"));
        call("verify", "{\"email\":\"test@gmail.com\",\"code\":\" 123 456 \"}").andExpect(status().isOk());
        var captor = ArgumentCaptor.forClass(PasswordResetVerifyRequest.class);
        verify(service).verifyCode(captor.capture());
        assertThat(captor.getValue().code()).isEqualTo("123456");
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "1234567", "abcdef", "12a456", "", "   "})
    void verifyRejectsAnythingThatIsNotSixDigits(String code) throws Exception {
        call("verify", "{\"email\":\"test@gmail.com\",\"code\":\"" + code + "\"}").andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void verifyRejectsAMissingCodeOrEmail() throws Exception {
        call("verify", "{\"email\":\"test@gmail.com\"}").andExpect(status().isBadRequest());
        call("verify", "{\"code\":\"123456\"}").andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void verifyMapsAnInvalidCodeTo401AndALockTo429() throws Exception {
        when(service.verifyCode(any())).thenThrow(new UnauthorizedException("Código inválido o vencido"));
        call("verify", VERIFY).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Código inválido o vencido"))
                .andExpect(jsonPath("$.email").doesNotExist());
        reset(service);
        when(service.verifyCode(any())).thenThrow(new TooManyRequestsException("Demasiados intentos"));
        call("verify", VERIFY).andExpect(status().isTooManyRequests());
    }

    // ---- paso 3

    @Test
    void confirmReturns200WithoutEchoingSecrets() throws Exception {
        when(service.resetPassword(any())).thenReturn(new PasswordResetResponse("Contraseña actualizada"));
        call("confirm", CONFIRM).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Contraseña actualizada"))
                .andExpect(jsonPath("$.newPassword").doesNotExist()).andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(content().string(not(containsString("Nueva1234"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "password123", "Password", "Ab1"})
    void confirmRejectsAWeakPasswordWithThePolicyMessageAndNeverEchoesIt(String password) throws Exception {
        ObjectNode body = (ObjectNode) mapper.readTree(CONFIRM);
        body.put("newPassword", password);
        call("confirm", body.toString()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La contraseña debe contener al menos 8 caracteres, una mayúscula y un número"))
                .andExpect(content().string(not(containsString(":\"" + password + "\""))));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"email", "code", "newPassword"})
    void confirmRejectsMissingNullOrBlankFields(String field) throws Exception {
        for (String mutation : new String[]{"remove", "null", "blank"}) {
            ObjectNode body = (ObjectNode) mapper.readTree(CONFIRM);
            switch (mutation) {
                case "remove" -> body.remove(field);
                case "null" -> body.putNull(field);
                default -> body.put(field, "   ");
            }
            call("confirm", body.toString()).andExpect(status().isBadRequest())
                    .andExpect(content().string(not(containsString("Nueva1234"))));
        }
        verifyNoInteractions(service);
    }

    @Test
    void confirmMapsServiceErrors() throws Exception {
        when(service.resetPassword(any())).thenThrow(new UnauthorizedException("Código inválido o vencido"));
        call("confirm", CONFIRM).andExpect(status().isUnauthorized());
        reset(service);
        when(service.resetPassword(any())).thenThrow(new BadRequestException("La contraseña no debe superar 72 bytes en UTF-8"));
        call("confirm", CONFIRM).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La contraseña no debe superar 72 bytes en UTF-8"));
        reset(service);
        when(service.resetPassword(any())).thenThrow(new TooManyRequestsException("Demasiados intentos"));
        call("confirm", CONFIRM).andExpect(status().isTooManyRequests());
    }
}

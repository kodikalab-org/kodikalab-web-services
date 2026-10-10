package com.kodika.kodikalab.auth;

import com.kodika.kodikalab.auth.dto.RegisterRequest;
import com.kodika.kodikalab.common.exception.BadRequestException;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.UserService;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceTests {
    @Test
    void hashesPasswordAndDelegatesThroughPublicUserService() {
        UserService users = mock(UserService.class);
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        AuthService service = new AuthServiceImpl(users, encoder);
        var result = service.register(new RegisterRequest("  Usuario  ", "  Prueba  ",
                "  TEST@GMAIL.COM  ", "Password123", Role.PRACTICANTE));
        assertThat(result.message()).isEqualTo("Registro exitoso");
        assertThat(result.email()).isEqualTo("test@gmail.com");
        assertThat(result.role()).isEqualTo(Role.PRACTICANTE);
        var hash = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(users).createUser(eq("Usuario Prueba"), eq("test@gmail.com"),
                hash.capture(), eq(Role.PRACTICANTE));
        assertThat(hash.getValue()).isNotEqualTo("Password123");
        assertThat(encoder.matches("Password123", hash.getValue())).isTrue();
    }

    @Test
    void rejectsPasswordExceedingBcryptByteLimitWithoutHashing() {
        UserService users = mock(UserService.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        AuthService service = new AuthServiceImpl(users, encoder);
        assertThatThrownBy(() -> service.register(new RegisterRequest("Usuario", "Prueba", "test@gmail.com",
                "A1" + "ñ".repeat(36), Role.COACH))).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(users, encoder);
    }

    @Test
    void validatedServiceContractRejectsWeakPasswordBeforeHashing() {
        UserService users = mock(UserService.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            ProxyFactory proxy = new ProxyFactory(new AuthServiceImpl(users, encoder));
            proxy.addAdvice(new MethodValidationInterceptor(factory.getValidator()));
            AuthService service = (AuthService) proxy.getProxy();
            assertThatThrownBy(() -> service.register(new RegisterRequest("Usuario", "Prueba", "test@gmail.com",
                    "12345", Role.PRACTICANTE))).isInstanceOf(ConstraintViolationException.class);
            verifyNoInteractions(users, encoder);
        }
    }

    @Test
    void rejectsCombinedNameOver150BeforeHashing() {
        UserService users = mock(UserService.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        AuthService service = new AuthServiceImpl(users, encoder);
        assertThatThrownBy(() -> service.register(new RegisterRequest("a".repeat(80), "b".repeat(70),
                "test@gmail.com", "Password123", Role.COACH)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("El nombre completo debe tener como máximo 150 caracteres");
        verifyNoInteractions(users, encoder);
    }

    @Test
    void acceptsCombinedNameExactly150() {
        UserService users = mock(UserService.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode("Password123")).thenReturn("HASH");
        new AuthServiceImpl(users, encoder).register(new RegisterRequest("a".repeat(80), "b".repeat(69),
                "test@gmail.com", "Password123", Role.COACH));
        verify(users).createUser("a".repeat(80) + " " + "b".repeat(69), "test@gmail.com", "HASH", Role.COACH);
    }

    @Test
    void requestStringNeverIncludesPassword() {
        var request = new RegisterRequest("Usuario", "Prueba", "test@gmail.com", "Password123", Role.COACH);
        assertThat(request.toString()).doesNotContain("Password123");
    }
}

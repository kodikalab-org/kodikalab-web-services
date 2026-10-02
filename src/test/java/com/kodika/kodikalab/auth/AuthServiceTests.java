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
        var result = service.register(new RegisterRequest("  Matias  ", "  Del Castillo  ",
                "  MATIAS@UPC.EDU.PE  ", "Password123", Role.PRACTITIONER));
        assertThat(result.message()).isEqualTo("Registro exitoso");
        assertThat(result.email()).isEqualTo("matias@upc.edu.pe");
        assertThat(result.role()).isEqualTo(Role.PRACTITIONER);
        var hash = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(users).createUser(eq("Matias"), eq("Del Castillo"), eq("matias@upc.edu.pe"),
                hash.capture(), eq(Role.PRACTITIONER));
        assertThat(hash.getValue()).isNotEqualTo("Password123");
        assertThat(encoder.matches("Password123", hash.getValue())).isTrue();
    }

    @Test
    void rejectsPasswordExceedingBcryptByteLimitWithoutHashing() {
        UserService users = mock(UserService.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        AuthService service = new AuthServiceImpl(users, encoder);
        assertThatThrownBy(() -> service.register(new RegisterRequest("Matias", "Test", "matias@upc.edu.pe",
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
            assertThatThrownBy(() -> service.register(new RegisterRequest("Matias", "Test", "matias@upc.edu.pe",
                    "12345", Role.PRACTITIONER))).isInstanceOf(ConstraintViolationException.class);
            verifyNoInteractions(users, encoder);
        }
    }

    @Test
    void requestStringNeverIncludesPassword() {
        var request = new RegisterRequest("Matias", "Test", "matias@upc.edu.pe", "Password123", Role.COACH);
        assertThat(request.toString()).doesNotContain("Password123");
    }
}

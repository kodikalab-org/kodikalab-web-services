package com.kodika.kodikalab.auth;

import com.kodika.kodikalab.auth.dto.LoginRequest;
import com.kodika.kodikalab.common.exception.UnauthorizedException;
import com.kodika.kodikalab.security.JwtService;
import com.kodika.kodikalab.support.TestJwt;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserService;
import com.kodika.kodikalab.users.UserStatus;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LoginServiceTests {
    UserService users;
    PasswordEncoder encoder;
    JwtService jwt = TestJwt.service();
    AuthService service;
    User user;

    @BeforeEach
    void setUp() {
        users = mock(UserService.class);
        encoder = mock(PasswordEncoder.class);
        service = new AuthServiceImpl(users, encoder, jwt);
        user = new User();
        user.setEmail("test@gmail.com");
        user.setPasswordHash("HASH");
        user.setStatus(UserStatus.ACTIVO);
        user.setRole(Role.PRACTICANTE);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void loginReturnsStoredRoleAndNoSecrets(Role role) {
        user.setRole(role);
        when(users.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));
        when(encoder.matches("Password123", "HASH")).thenReturn(true);
        var result = service.login(new LoginRequest("  TEST@GMAIL.COM  ", "Password123"));
        assertThat(result.message()).isEqualTo("Inicio de sesión exitoso");
        assertThat(result.email()).isEqualTo(user.getEmail());
        assertThat(result.role()).isEqualTo(role);
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.expiresIn()).isEqualTo(TestJwt.EXPIRATION_MILLIS / 1000);
        assertThat(jwt.parse(result.token())).get().satisfies(claims -> {
            assertThat(claims.email()).isEqualTo(user.getEmail());
            assertThat(claims.role()).isEqualTo(role.name());
        });
        verify(encoder, never()).encode(anyString());
        verify(users, never()).createUser(any(), any(), any(), any());
    }

    @Test
    void incorrectPasswordIsUnauthorized() {
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        assertUnauthorized(new LoginRequest(user.getEmail(), "Wrong123"));
        verify(encoder).matches("Wrong123", "HASH");
    }

    @Test
    void missingUserIsUnauthorizedButStillPerformsBcryptComparison() {
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.empty());
        assertUnauthorized(new LoginRequest(user.getEmail(), "Password123"));
        verify(encoder).matches(eq("Password123"), startsWith("$2a$10$"));
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"SUSPENDIDO"})
    void disabledAccountUsesSameGenericError(UserStatus status) {
        user.setStatus(status);
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(encoder.matches("Password123", "HASH")).thenReturn(true);
        assertUnauthorized(new LoginRequest(user.getEmail(), "Password123"));
        assertThat(user.getStatus()).isEqualTo(status);
    }

    @Test
    void oversizedPasswordIsNotSilentlyTruncated() {
        assertUnauthorized(new LoginRequest(user.getEmail(), "A1" + "ñ".repeat(36)));
        verifyNoInteractions(users, encoder);
    }

    @Test
    void missingHashCannotAuthenticateEvenIfDummyComparisonMatches() {
        user.setPasswordHash(null);
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(encoder.matches(anyString(), anyString())).thenReturn(true);
        assertUnauthorized(new LoginRequest(user.getEmail(), "Password123"));
    }

    @Test
    void missingRoleCannotAuthenticate() {
        user.setRole(null);
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(encoder.matches("Password123", "HASH")).thenReturn(true);
        assertUnauthorized(new LoginRequest(user.getEmail(), "Password123"));
    }

    @Test
    void preservesPasswordSpacesAndUsesRealBcrypt() {
        PasswordEncoder bcrypt = new BCryptPasswordEncoder();
        String password = " Password123 ";
        user.setPasswordHash(bcrypt.encode(password));
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        AuthService realService = new AuthServiceImpl(users, bcrypt, jwt);
        assertThat(realService.login(new LoginRequest(user.getEmail(), password)).role()).isEqualTo(Role.PRACTICANTE);
        assertThatThrownBy(() -> realService.login(new LoginRequest(user.getEmail(), password.strip())))
                .isInstanceOf(UnauthorizedException.class).hasMessage("Credenciales inválidas");
    }

    @Test
    void serviceValidationRejectsMissingPasswordBeforeLookup() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            ProxyFactory proxy = new ProxyFactory(service);
            proxy.addAdvice(new MethodValidationInterceptor(factory.getValidator()));
            AuthService validated = (AuthService) proxy.getProxy();
            assertThatThrownBy(() -> validated.login(new LoginRequest(user.getEmail(), null)))
                    .isInstanceOf(ConstraintViolationException.class);
            verifyNoInteractions(users, encoder);
        }
    }

    @Test
    void loginRequestStringRedactsPassword() {
        assertThat(new LoginRequest(user.getEmail(), "Password123").toString()).doesNotContain("Password123");
    }

    private void assertUnauthorized(LoginRequest request) {
        assertThatThrownBy(() -> service.login(request)).isInstanceOf(UnauthorizedException.class)
                .hasMessage("Credenciales inválidas");
    }
}

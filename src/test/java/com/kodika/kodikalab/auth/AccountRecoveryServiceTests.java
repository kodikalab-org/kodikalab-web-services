package com.kodika.kodikalab.auth;

import com.kodika.kodikalab.auth.dto.RecoveryCodeRequest;
import com.kodika.kodikalab.auth.dto.RecoveryRequest;
import com.kodika.kodikalab.common.exception.BadRequestException;
import com.kodika.kodikalab.common.exception.UnauthorizedException;
import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.support.TestJwt;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserService;
import com.kodika.kodikalab.users.UserStatus;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountRecoveryServiceTests {
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final RecoveryCodeService codes = TestJwt.recoveryCodes();
    private final UserService users = mock(UserService.class);
    private final CurrentUserResolver current = mock(CurrentUserResolver.class);
    private AccountRecoveryService service;
    private User user;
    private String currentHash;

    @BeforeEach
    void setUp() {
        service = new AccountRecoveryServiceImpl(users, encoder, codes, current);
        currentHash = encoder.encode("Password123");
        user = new User();
        user.setId(5);
        user.setEmail("test@gmail.com");
        user.setPasswordHash(currentHash);
        user.setRole(Role.PRACTICANTE);
        user.setStatus(UserStatus.ACTIVO);
        when(users.findByEmail("test@gmail.com")).thenReturn(Optional.of(user));
        when(users.replacePasswordHash(eq(5), eq(currentHash), anyString())).thenReturn(true);
    }

    private RecoveryRequest request(String email, String code, String newPassword) {
        return new RecoveryRequest(email, code, newPassword);
    }

    private String validCode() {
        return codes.codeFor("test@gmail.com", currentHash);
    }

    private void assertRejected(RecoveryRequest request) {
        assertThatThrownBy(() -> service.recover(request)).isInstanceOf(UnauthorizedException.class)
                .hasMessage("Datos de recuperación inválidos");
    }

    @Test
    void validCodeDefinesTheNewPasswordAndReturnsTheNewCode() {
        var response = service.recover(request("  TEST@GMAIL.COM ", validCode().toLowerCase(), "Nueva1234"));
        var newHash = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(users).replacePasswordHash(eq(5), eq(currentHash), newHash.capture());
        assertThat(encoder.matches("Nueva1234", newHash.getValue())).isTrue();
        assertThat(newHash.getValue()).isNotEqualTo("Nueva1234");
        assertThat(response.message()).contains("Contraseña actualizada");
        assertThat(response.recoveryCode()).isEqualTo(codes.codeFor("test@gmail.com", newHash.getValue()))
                .isNotEqualTo(validCode());
    }

    @Test
    void theOldCodeStopsWorkingOnceThePasswordChanged() {
        String oldCode = validCode();
        service.recover(request("test@gmail.com", oldCode, "Nueva1234"));
        var newHash = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(users).replacePasswordHash(eq(5), eq(currentHash), newHash.capture());
        user.setPasswordHash(newHash.getValue());
        when(users.replacePasswordHash(eq(5), eq(newHash.getValue()), anyString())).thenReturn(true);
        assertRejected(request("test@gmail.com", oldCode, "Otra12345"));
    }

    @Test
    void wrongCodeIsRejectedWithoutChangingAnything() {
        assertRejected(request("test@gmail.com", "AAAA-BBBB-CCCC-DDDD-EEEE-FFFF", "Nueva1234"));
        verify(users, never()).replacePasswordHash(org.mockito.ArgumentMatchers.any(), anyString(), anyString());
    }

    @Test
    void unknownAccountIsRejectedWithTheSameError() {
        when(users.findByEmail("nadie@gmail.com")).thenReturn(Optional.empty());
        assertRejected(request("nadie@gmail.com", validCode(), "Nueva1234"));
        verify(users, never()).replacePasswordHash(org.mockito.ArgumentMatchers.any(), anyString(), anyString());
    }

    @Test
    void suspendedAccountCannotRecoverEvenWithTheRightCode() {
        user.setStatus(UserStatus.SUSPENDIDO);
        assertRejected(request("test@gmail.com", validCode(), "Nueva1234"));
        verify(users, never()).replacePasswordHash(org.mockito.ArgumentMatchers.any(), anyString(), anyString());
    }

    @Test
    void accountWithoutRoleOrHashCannotRecover() {
        user.setRole(null);
        assertRejected(request("test@gmail.com", validCode(), "Nueva1234"));
        user.setRole(Role.COACH);
        user.setPasswordHash(null);
        assertRejected(request("test@gmail.com", "AAAA-BBBB-CCCC-DDDD-EEEE-FFFF", "Nueva1234"));
    }

    @Test
    void aConcurrentChangeLosesTheRace() {
        when(users.replacePasswordHash(eq(5), eq(currentHash), anyString())).thenReturn(false);
        assertRejected(request("test@gmail.com", validCode(), "Nueva1234"));
    }

    @Test
    void newPasswordOver72BytesIsRejectedBeforeLookingUpTheAccount() {
        assertThatThrownBy(() -> service.recover(request("test@gmail.com", validCode(), "A1" + "ñ".repeat(36))))
                .isInstanceOf(BadRequestException.class).hasMessage("La contraseña no debe superar 72 bytes en UTF-8");
        verify(users, never()).findByEmail(anyString());
    }

    @Test
    void showsTheCurrentCodeToTheAuthenticatedAccountThatKnowsItsPassword() {
        when(current.currentUser()).thenReturn(user);
        var response = service.showRecoveryCode(new RecoveryCodeRequest("Password123"));
        assertThat(response.recoveryCode()).isEqualTo(validCode());
        assertThat(response.message()).contains("Código de recuperación vigente");
    }

    @Test
    void wrongOrOversizedPasswordDoesNotRevealTheCode() {
        when(current.currentUser()).thenReturn(user);
        for (String password : new String[]{"Wrong1234", "A1" + "ñ".repeat(36)}) {
            assertThatThrownBy(() -> service.showRecoveryCode(new RecoveryCodeRequest(password)))
                    .isInstanceOf(UnauthorizedException.class).hasMessage("Credenciales inválidas");
        }
    }

    @Test
    void secretsNeverAppearInToStringOutput() {
        assertThat(new RecoveryRequest("test@gmail.com", "AAAA-BBBB", "Nueva1234").toString())
                .doesNotContain("AAAA-BBBB").doesNotContain("Nueva1234");
        assertThat(new RecoveryCodeRequest("Password123").toString()).doesNotContain("Password123");
        assertThat(new com.kodika.kodikalab.auth.dto.RecoveryResponse("ok", "AAAA-BBBB").toString()).doesNotContain("AAAA-BBBB");
    }
}

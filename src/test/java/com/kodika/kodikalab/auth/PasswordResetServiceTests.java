package com.kodika.kodikalab.auth;

import com.kodika.kodikalab.auth.dto.PasswordResetConfirmRequest;
import com.kodika.kodikalab.auth.dto.PasswordResetRequest;
import com.kodika.kodikalab.auth.dto.PasswordResetVerifyRequest;
import com.kodika.kodikalab.common.exception.BadRequestException;
import com.kodika.kodikalab.common.exception.ServiceUnavailableException;
import com.kodika.kodikalab.common.exception.TooManyRequestsException;
import com.kodika.kodikalab.common.exception.UnauthorizedException;
import com.kodika.kodikalab.support.MutableClock;
import com.kodika.kodikalab.support.TestJwt;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserService;
import com.kodika.kodikalab.users.UserStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PasswordResetServiceTests {
    private static final String EMAIL = "test@gmail.com";

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final UserService users = mock(UserService.class);
    private final PasswordResetMailer mailer = mock(PasswordResetMailer.class);
    private MutableClock clock;
    private PasswordResetCodeService codes;
    private PasswordResetAttemptLimiter limiter;
    private PasswordResetService service;
    private User user;
    private String currentHash;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-06T12:00:00Z"));
        codes = new PasswordResetCodeService(TestJwt.service().deriveKey("kodikalab:password-reset-code:v1"), clock);
        limiter = new PasswordResetAttemptLimiter(clock);
        service = new PasswordResetServiceImpl(users, encoder, codes, limiter, mailer);
        // El titular ya pidió un código hace un minuto: verify y confirm solo aceptan códigos que se pidieron.
        limiter.acquireSend(EMAIL);
        clock.advance(Duration.ofSeconds(60));
        currentHash = encoder.encode("Password123");
        user = new User();
        user.setId(5);
        user.setFullName("Camila Rojas");
        user.setEmail(EMAIL);
        user.setPasswordHash(currentHash);
        user.setRole(Role.PRACTICANTE);
        user.setStatus(UserStatus.ACTIVO);
        when(mailer.isConfigured()).thenReturn(true);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(users.replacePasswordHash(eq(5), eq(currentHash), anyString())).thenReturn(true);
    }

    private String validCode() {
        return codes.currentCode(EMAIL, currentHash);
    }

    private String wrongCode() {
        return String.format("%06d", (Integer.parseInt(validCode()) + 1) % 1_000_000);
    }

    private PasswordResetVerifyRequest verifyRequest(String code) {
        return new PasswordResetVerifyRequest(EMAIL, code);
    }

    private PasswordResetConfirmRequest confirm(String code, String newPassword) {
        return new PasswordResetConfirmRequest(EMAIL, code, newPassword);
    }

    private void assertInvalidCode(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(UnauthorizedException.class).hasMessage("Código inválido o vencido");
    }

    // ---- paso 1: pedir el código

    @Test
    void requestSendsTheCurrentCodeToAnActiveAccount() {
        var response = service.requestCode(new PasswordResetRequest("  TEST@GMAIL.COM "));
        verify(mailer).sendCode(EMAIL, "Camila Rojas", validCode());
        assertThat(response.message()).isEqualTo(PasswordResetServiceImpl.REQUEST_ACCEPTED);
    }

    @Test
    void requestAnswersTheSameAndSendsNothingForAnUnknownEmail() {
        when(users.findByEmail("nadie@gmail.com")).thenReturn(Optional.empty());
        var response = service.requestCode(new PasswordResetRequest("nadie@gmail.com"));
        verify(mailer, never()).sendCode(anyString(), anyString(), anyString());
        assertThat(response.message()).isEqualTo(PasswordResetServiceImpl.REQUEST_ACCEPTED);
    }

    @Test
    void requestSendsNothingToASuspendedAccountOrOneWithoutRoleOrHash() {
        user.setStatus(UserStatus.SUSPENDIDO);
        assertThat(service.requestCode(new PasswordResetRequest(EMAIL)).message())
                .isEqualTo(PasswordResetServiceImpl.REQUEST_ACCEPTED);
        clock.advance(Duration.ofMinutes(2));
        user.setStatus(UserStatus.ACTIVO);
        user.setRole(null);
        service.requestCode(new PasswordResetRequest(EMAIL));
        clock.advance(Duration.ofMinutes(2));
        user.setRole(Role.COACH);
        user.setPasswordHash(null);
        service.requestCode(new PasswordResetRequest(EMAIL));
        verify(mailer, never()).sendCode(anyString(), anyString(), anyString());
    }

    @Test
    void requestFailsWith503WhenTheServerCannotSendMailWithoutLookingAnyAccountUp() {
        when(mailer.isConfigured()).thenReturn(false);
        assertThatThrownBy(() -> service.requestCode(new PasswordResetRequest(EMAIL)))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessage("El envío de correos no está configurado");
        verifyNoInteractions(users);
        verify(mailer, never()).sendCode(anyString(), anyString(), anyString());
    }

    @Test
    void requestIsLimitedToOneSendPerMinuteAndTheLimitAlsoAppliesToUnknownEmails() {
        service.requestCode(new PasswordResetRequest(EMAIL));
        assertThatThrownBy(() -> service.requestCode(new PasswordResetRequest(EMAIL)))
                .isInstanceOf(TooManyRequestsException.class);
        verify(mailer, times(1)).sendCode(anyString(), anyString(), anyString());
        when(users.findByEmail("nadie@gmail.com")).thenReturn(Optional.empty());
        service.requestCode(new PasswordResetRequest("nadie@gmail.com"));
        assertThatThrownBy(() -> service.requestCode(new PasswordResetRequest("nadie@gmail.com")))
                .isInstanceOf(TooManyRequestsException.class);
        clock.advance(Duration.ofSeconds(60));
        assertThatCode(() -> service.requestCode(new PasswordResetRequest(EMAIL))).doesNotThrowAnyException();
    }

    // ---- paso 2: comprobar el código

    @Test
    void verifyAcceptsTheRightCodeWithoutConsumingIt() {
        assertThat(service.verifyCode(verifyRequest(validCode())).message()).contains("Código verificado");
        assertThat(service.verifyCode(verifyRequest(validCode())).message()).contains("Código verificado");
        verify(users, never()).replacePasswordHash(any(), anyString(), anyString());
    }

    @Test
    void verifyRejectsTheRightCodeWhenNoCodeWasRequestedForThatEmail() {
        limiter.clear(EMAIL);
        assertInvalidCode(() -> service.verifyCode(verifyRequest(validCode())));
        assertInvalidCode(() -> service.resetPassword(confirm(validCode(), "Nueva1234")));
        verify(users, never()).replacePasswordHash(any(), anyString(), anyString());
    }

    @Test
    void aRequestedCodeIsAcceptedUntilTwentyMinutesAfterTheSendAndRejectedAfterwards() {
        clock.advance(Duration.ofMinutes(19));
        assertThat(service.verifyCode(verifyRequest(validCode())).message()).contains("Código verificado");
        clock.advance(Duration.ofMinutes(2));
        assertInvalidCode(() -> service.verifyCode(verifyRequest(validCode())));
    }

    @Test
    void verifyRejectsAWrongOrExpiredCodeWithOneGenericError() {
        assertInvalidCode(() -> service.verifyCode(verifyRequest(wrongCode())));
        String expired = validCode();
        clock.advance(Duration.ofMinutes(21));
        assertInvalidCode(() -> service.verifyCode(verifyRequest(expired)));
    }

    @Test
    void verifyAnswersTheSameForUnknownAndSuspendedAccounts() {
        when(users.findByEmail("nadie@gmail.com")).thenReturn(Optional.empty());
        assertInvalidCode(() -> service.verifyCode(new PasswordResetVerifyRequest("nadie@gmail.com", "123456")));
        String code = validCode();
        user.setStatus(UserStatus.SUSPENDIDO);
        assertInvalidCode(() -> service.verifyCode(verifyRequest(code)));
    }

    @Test
    void verifyLocksTheEmailAfterFiveWrongCodesEvenForTheRightCode() {
        for (int i = 0; i < 5; i++) {
            assertInvalidCode(() -> service.verifyCode(verifyRequest(wrongCode())));
        }
        assertThatThrownBy(() -> service.verifyCode(verifyRequest(validCode()))).isInstanceOf(TooManyRequestsException.class);
        assertThatThrownBy(() -> service.resetPassword(confirm(validCode(), "Nueva1234")))
                .isInstanceOf(TooManyRequestsException.class);
        verify(users, never()).replacePasswordHash(any(), anyString(), anyString());
    }

    @Test
    void failuresOnAnUnknownEmailAlsoCountSoTheLockCannotRevealWhichAccountsExist() {
        when(users.findByEmail("nadie@gmail.com")).thenReturn(Optional.empty());
        for (int i = 0; i < 5; i++) {
            assertInvalidCode(() -> service.verifyCode(new PasswordResetVerifyRequest("nadie@gmail.com", "123456")));
        }
        assertThatThrownBy(() -> service.verifyCode(new PasswordResetVerifyRequest("nadie@gmail.com", "123456")))
                .isInstanceOf(TooManyRequestsException.class);
    }

    // ---- paso 3: definir la contraseña nueva

    @Test
    void resetDefinesTheNewPasswordWithBcrypt() {
        var response = service.resetPassword(confirm(validCode(), "Nueva1234"));
        var newHash = ArgumentCaptor.forClass(String.class);
        verify(users).replacePasswordHash(eq(5), eq(currentHash), newHash.capture());
        assertThat(encoder.matches("Nueva1234", newHash.getValue())).isTrue();
        assertThat(newHash.getValue()).isNotEqualTo("Nueva1234");
        assertThat(response.message()).contains("Contraseña actualizada");
    }

    @Test
    void theCodeStopsWorkingOnceThePasswordChanged() {
        String code = validCode();
        service.resetPassword(confirm(code, "Nueva1234"));
        var newHash = ArgumentCaptor.forClass(String.class);
        verify(users).replacePasswordHash(eq(5), eq(currentHash), newHash.capture());
        user.setPasswordHash(newHash.getValue());
        assertInvalidCode(() -> service.verifyCode(verifyRequest(code)));
        assertInvalidCode(() -> service.resetPassword(confirm(code, "Otra12345")));
    }

    @Test
    void aWrongCodeChangesNothing() {
        assertInvalidCode(() -> service.resetPassword(confirm(wrongCode(), "Nueva1234")));
        verify(users, never()).replacePasswordHash(any(), anyString(), anyString());
    }

    @Test
    void aConcurrentChangeLosesTheRace() {
        when(users.replacePasswordHash(eq(5), eq(currentHash), anyString())).thenReturn(false);
        assertInvalidCode(() -> service.resetPassword(confirm(validCode(), "Nueva1234")));
    }

    @Test
    void aNewPasswordOver72BytesIsRejectedBeforeLookingUpTheAccount() {
        assertThatThrownBy(() -> service.resetPassword(confirm(validCode(), "A1" + "ñ".repeat(36))))
                .isInstanceOf(BadRequestException.class).hasMessage("La contraseña no debe superar 72 bytes en UTF-8");
        verify(users, never()).findByEmail(anyString());
    }

    @Test
    void aSuccessfulResetForgetsTheFailedAttempts() {
        for (int i = 0; i < 4; i++) {
            assertInvalidCode(() -> service.verifyCode(verifyRequest(wrongCode())));
        }
        service.resetPassword(confirm(validCode(), "Nueva1234"));
        for (int i = 0; i < 4; i++) {
            assertInvalidCode(() -> service.verifyCode(verifyRequest(wrongCode())));
        }
        // El quinto fallo posterior al reinicio todavía responde 401 y no 429: el contador empezó de cero.
        assertInvalidCode(() -> service.verifyCode(verifyRequest(wrongCode())));
    }

    @Test
    void secretsNeverAppearInToStringOutput() {
        assertThat(confirm("123456", "Nueva1234").toString()).doesNotContain("123456").doesNotContain("Nueva1234");
        assertThat(verifyRequest("123456").toString()).doesNotContain("123456");
    }
}

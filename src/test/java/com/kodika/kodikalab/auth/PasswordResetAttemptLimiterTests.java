package com.kodika.kodikalab.auth;

import com.kodika.kodikalab.common.exception.TooManyRequestsException;
import com.kodika.kodikalab.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordResetAttemptLimiterTests {
    private MutableClock clock;
    private PasswordResetAttemptLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-06T12:00:00Z"));
        limiter = new PasswordResetAttemptLimiter(clock);
    }

    private void fail(String email, int times) {
        for (int i = 0; i < times; i++) {
            limiter.recordFailure(email);
        }
    }

    @Test
    void onlyOneSendPerMinutePerEmail() {
        limiter.acquireSend("a@gmail.com");
        assertThatThrownBy(() -> limiter.acquireSend("a@gmail.com"))
                .isInstanceOf(TooManyRequestsException.class).hasMessage("Espera un minuto antes de pedir otro código");
        clock.advance(Duration.ofSeconds(59));
        assertThatThrownBy(() -> limiter.acquireSend("a@gmail.com")).isInstanceOf(TooManyRequestsException.class);
        clock.advance(Duration.ofSeconds(1));
        assertThatCode(() -> limiter.acquireSend("a@gmail.com")).doesNotThrowAnyException();
    }

    @Test
    void sendLimitsAreIndependentPerEmail() {
        limiter.acquireSend("a@gmail.com");
        assertThatCode(() -> limiter.acquireSend("b@gmail.com")).doesNotThrowAnyException();
    }

    @Test
    void aRejectedSendDoesNotExtendTheWait() {
        limiter.acquireSend("a@gmail.com");
        clock.advance(Duration.ofSeconds(30));
        assertThatThrownBy(() -> limiter.acquireSend("a@gmail.com")).isInstanceOf(TooManyRequestsException.class);
        clock.advance(Duration.ofSeconds(30));
        assertThatCode(() -> limiter.acquireSend("a@gmail.com")).doesNotThrowAnyException();
    }

    @Test
    void theFifthWrongCodeLocksTheEmail() {
        fail("a@gmail.com", 4);
        assertThatCode(() -> limiter.checkNotLocked("a@gmail.com")).doesNotThrowAnyException();
        limiter.recordFailure("a@gmail.com");
        assertThatThrownBy(() -> limiter.checkNotLocked("a@gmail.com"))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessage("Demasiados intentos. Espera unos minutos y pide un código nuevo");
    }

    @Test
    void theLockEndsWhenTheFailureWindowPasses() {
        fail("a@gmail.com", 5);
        clock.advance(Duration.ofMinutes(15));
        assertThatThrownBy(() -> limiter.checkNotLocked("a@gmail.com")).isInstanceOf(TooManyRequestsException.class);
        clock.advance(Duration.ofSeconds(1));
        assertThatCode(() -> limiter.checkNotLocked("a@gmail.com")).doesNotThrowAnyException();
    }

    @Test
    void failuresOlderThanTheWindowStartANewCount() {
        fail("a@gmail.com", 4);
        clock.advance(Duration.ofMinutes(16));
        fail("a@gmail.com", 4);
        assertThatCode(() -> limiter.checkNotLocked("a@gmail.com")).doesNotThrowAnyException();
    }

    @Test
    void lockingOneEmailDoesNotAffectAnother() {
        fail("a@gmail.com", 5);
        assertThatCode(() -> limiter.checkNotLocked("b@gmail.com")).doesNotThrowAnyException();
    }

    @Test
    void aSuccessfulResetForgetsThePreviousFailures() {
        fail("a@gmail.com", 4);
        limiter.clear("a@gmail.com");
        fail("a@gmail.com", 4);
        assertThatCode(() -> limiter.checkNotLocked("a@gmail.com")).doesNotThrowAnyException();
    }
}

package com.kodika.kodikalab.teams.groupmembership;

import com.kodika.kodikalab.common.exception.TooManyRequestsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvitationCodeAttemptLimiterTests {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-06T10:00:00Z"));
    private InvitationCodeAttemptLimiter limiter;

    @BeforeEach
    void setUp() {
        Clock clock = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
        limiter = new InvitationCodeAttemptLimiter(clock);
    }

    @Test
    void allowsFiveAttemptsAndRejectsTheSixthWith429() {
        for (int i = 0; i < InvitationCodeAttemptLimiter.MAX_ATTEMPTS; i++) {
            limiter.reserveAttempt(2);
        }

        assertThatThrownBy(() -> limiter.reserveAttempt(2))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessage("Demasiados intentos con códigos de invitación. Espera unos minutos e inténtalo de nuevo");
    }

    @Test
    void aRefundedAttemptDoesNotCountTowardsTheLimit() {
        for (int i = 0; i < InvitationCodeAttemptLimiter.MAX_ATTEMPTS; i++) {
            Instant window = limiter.reserveAttempt(2);
            limiter.refundAttempt(2, window);
        }

        assertThatCode(() -> limiter.reserveAttempt(2)).doesNotThrowAnyException();
    }

    @Test
    void theLimitResetsWhenTheWindowExpires() {
        for (int i = 0; i < InvitationCodeAttemptLimiter.MAX_ATTEMPTS; i++) {
            limiter.reserveAttempt(2);
        }

        now.set(now.get().plus(InvitationCodeAttemptLimiter.FAILURE_WINDOW).plus(Duration.ofSeconds(1)));

        assertThatCode(() -> limiter.reserveAttempt(2)).doesNotThrowAnyException();
    }

    @Test
    void eachPractitionerHasItsOwnLimit() {
        for (int i = 0; i < InvitationCodeAttemptLimiter.MAX_ATTEMPTS; i++) {
            limiter.reserveAttempt(2);
        }

        assertThatCode(() -> limiter.reserveAttempt(3)).doesNotThrowAnyException();
    }
}

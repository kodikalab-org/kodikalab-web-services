package com.kodika.kodikalab.auth;

import com.kodika.kodikalab.common.exception.TooManyRequestsException;
import com.kodika.kodikalab.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
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

    private void reserve(String email, int times) {
        for (int i = 0; i < times; i++) {
            limiter.reserveAttempt(email);
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
    void aCodeCountsAsRequestedOnlyForTwentyMinutesAfterTheLastSend() {
        assertThat(limiter.codeRequestedRecently("a@gmail.com")).isFalse();
        limiter.acquireSend("a@gmail.com");
        assertThat(limiter.codeRequestedRecently("a@gmail.com")).isTrue();
        assertThat(limiter.codeRequestedRecently("b@gmail.com")).isFalse();
        clock.advance(Duration.ofMinutes(20));
        assertThat(limiter.codeRequestedRecently("a@gmail.com")).isTrue();
        clock.advance(Duration.ofSeconds(1));
        assertThat(limiter.codeRequestedRecently("a@gmail.com")).isFalse();
    }

    @Test
    void onlyFiveAttemptsAreAllowedAndTheSixthIsRejectedBeforeAnythingIsChecked() {
        reserve("a@gmail.com", 5);
        assertThatThrownBy(() -> limiter.reserveAttempt("a@gmail.com"))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessage("Demasiados intentos. Espera unos minutos y pide un código nuevo");
    }

    @Test
    void theQuotaEndsWhenTheWindowPasses() {
        reserve("a@gmail.com", 5);
        clock.advance(Duration.ofMinutes(15));
        assertThatThrownBy(() -> limiter.reserveAttempt("a@gmail.com")).isInstanceOf(TooManyRequestsException.class);
        clock.advance(Duration.ofSeconds(1));
        assertThatCode(() -> limiter.reserveAttempt("a@gmail.com")).doesNotThrowAnyException();
    }

    @Test
    void attemptsOlderThanTheWindowStartANewCount() {
        reserve("a@gmail.com", 4);
        clock.advance(Duration.ofMinutes(16));
        reserve("a@gmail.com", 5);
        assertThatThrownBy(() -> limiter.reserveAttempt("a@gmail.com")).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void exhaustingOneEmailDoesNotAffectAnother() {
        reserve("a@gmail.com", 5);
        assertThatCode(() -> limiter.reserveAttempt("b@gmail.com")).doesNotThrowAnyException();
    }

    @Test
    void aRefundedAttemptGivesTheSlotBack() {
        reserve("a@gmail.com", 4);
        Instant window = limiter.reserveAttempt("a@gmail.com");
        limiter.refundAttempt("a@gmail.com", window);
        assertThatCode(() -> limiter.reserveAttempt("a@gmail.com")).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.reserveAttempt("a@gmail.com")).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void aRefundForAnOlderWindowDoesNotTouchTheCurrentOne() {
        Instant oldWindow = limiter.reserveAttempt("a@gmail.com");
        clock.advance(Duration.ofMinutes(16));
        reserve("a@gmail.com", 5);
        limiter.refundAttempt("a@gmail.com", oldWindow);
        assertThatThrownBy(() -> limiter.reserveAttempt("a@gmail.com")).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void aSuccessfulResetForgetsTheAttemptsAndTheRequest() {
        limiter.acquireSend("a@gmail.com");
        reserve("a@gmail.com", 4);
        limiter.clear("a@gmail.com");
        assertThat(limiter.codeRequestedRecently("a@gmail.com")).isFalse();
        assertThatCode(() -> reserve("a@gmail.com", 5)).doesNotThrowAnyException();
    }

    @Test
    void spellingVariantsOfTheSameEmailShareTheQuota() {
        // El texto plegado como lo hace upper() en la base de datos: ı (U+0131) → I, ſ (U+017F) → S.
        reserve("cila@x.com", 2);
        reserve("CILA@x.com", 1);
        reserve(" cıla@x.com ", 1);
        reserve("cıla@x.com", 1);
        assertThatThrownBy(() -> limiter.reserveAttempt("cila@X.COM")).isInstanceOf(TooManyRequestsException.class);
        assertThat(PasswordResetAttemptLimiter.key("caſa@x.com")).isEqualTo("casa@x.com");
    }

    @Test
    void simultaneousAttemptsCannotEvaluateMoreCodesThanTheQuota() throws Exception {
        int threads = 200;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.execute(() -> {
                ready.countDown();
                try {
                    go.await();
                    limiter.reserveAttempt("a@gmail.com");
                    allowed.incrementAndGet();
                } catch (TooManyRequestsException exception) {
                    rejected.incrementAndGet();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        ready.await();
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        assertThat(allowed.get()).isEqualTo(5);
        assertThat(rejected.get()).isEqualTo(threads - 5);
    }

    @Test
    void expiredEntriesAreEvictedEvenWhenNobodyRequestsCodes() {
        for (int i = 0; i < 10_001; i++) {
            limiter.reserveAttempt("user" + i + "@gmail.com");
        }
        clock.advance(Duration.ofMinutes(21));
        limiter.reserveAttempt("otro@gmail.com");
        // Tras el barrido solo queda la entrada nueva: las anteriores ya habían vencido y se descartaron.
        assertThat(limiter.trackedAttempts()).isEqualTo(1);
    }

    @Test
    void theSweepRunsAtMostOncePerMinute() {
        for (int i = 0; i < 10_001; i++) {
            limiter.reserveAttempt("user" + i + "@gmail.com");
        }
        clock.advance(Duration.ofSeconds(30));
        limiter.reserveAttempt("otro@gmail.com");
        assertThat(limiter.trackedAttempts()).isEqualTo(10_002);
    }
}

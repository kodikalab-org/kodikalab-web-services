package com.kodika.kodikalab.teams.groupmembership;

import com.kodika.kodikalab.common.exception.TooManyRequestsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Límite de intentos de {@code POST /teams/join-by-code}, por practicante y en memoria (US05).
 *
 * <p>El código de invitación es la única credencial para entrar a un grupo protegido, así que se aceptan como mucho
 * 5 códigos inexistentes en 15 minutos por cuenta. El intento se reserva antes de buscar el código y se devuelve si el
 * código existía; así las peticiones simultáneas no pueden probar más códigos que el cupo. Igual que
 * {@code PasswordResetAttemptLimiter}, se reinicia con la aplicación y no se comparte entre instancias.
 */
@Component
public class InvitationCodeAttemptLimiter {
    static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);
    static final int MAX_ATTEMPTS = 5;
    private static final int PURGE_THRESHOLD = 10_000;

    private final Clock clock;
    private final Map<Integer, Attempts> attempts = new ConcurrentHashMap<>();

    public InvitationCodeAttemptLimiter() {
        this(Clock.systemUTC());
    }

    InvitationCodeAttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Reserva un intento o falla con 429; devuelve la ventana para poder devolverlo con {@link #refundAttempt}. */
    public Instant reserveAttempt(Integer userId) {
        Instant now = clock.instant();
        purgeIfLarge(now);
        Instant[] window = new Instant[1];
        boolean[] exhausted = {false};
        attempts.compute(userId, (key, current) -> {
            if (current == null || current.expired(now)) {
                window[0] = now;
                return new Attempts(1, now);
            }
            if (current.count() >= MAX_ATTEMPTS) {
                exhausted[0] = true;
                return current;
            }
            window[0] = current.since();
            return new Attempts(current.count() + 1, current.since());
        });
        if (exhausted[0]) {
            throw new TooManyRequestsException(
                    "Demasiados intentos con códigos de invitación. Espera unos minutos e inténtalo de nuevo");
        }
        return window[0];
    }

    /** Devuelve el intento cuando el código existía (solo si la ventana sigue siendo la misma). */
    public void refundAttempt(Integer userId, Instant window) {
        attempts.computeIfPresent(userId, (key, current) ->
                current.since().equals(window) && current.count() > 0
                        ? new Attempts(current.count() - 1, current.since()) : current);
    }

    private void purgeIfLarge(Instant now) {
        if (attempts.size() > PURGE_THRESHOLD) {
            attempts.values().removeIf(entry -> entry.expired(now));
        }
    }

    private record Attempts(int count, Instant since) {
        boolean expired(Instant now) {
            return now.isAfter(since.plus(FAILURE_WINDOW));
        }
    }
}

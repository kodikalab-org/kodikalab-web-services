package com.kodika.kodikalab.auth;

import com.kodika.kodikalab.common.exception.TooManyRequestsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Límites del restablecimiento de contraseña, por correo y en memoria (sin tablas nuevas):
 * <ul>
 *   <li>Envío: como mucho un correo por minuto, para que nadie use la API como emisor de correo masivo.</li>
 *   <li>Verificación: tras 5 códigos incorrectos en 15 minutos se bloquea el correo hasta que pase la ventana.
 *       Es lo que hace viable un código de solo 6 dígitos.</li>
 * </ul>
 * Los límites se aplican igual a correos registrados y no registrados, para no revelar qué cuentas existen. Al ser en
 * memoria se reinician con la aplicación y no se comparten entre instancias: es suficiente para el monolito actual.
 */
@Component
public class PasswordResetAttemptLimiter {
    static final Duration SEND_INTERVAL = Duration.ofSeconds(60);
    static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);
    static final int MAX_FAILURES = 5;
    private static final int PURGE_THRESHOLD = 10_000;

    private final Clock clock;
    private final Map<String, Instant> lastSend = new ConcurrentHashMap<>();
    private final Map<String, Failures> failures = new ConcurrentHashMap<>();

    public PasswordResetAttemptLimiter() {
        this(Clock.systemUTC());
    }

    PasswordResetAttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Registra un envío o falla con 429 si el último fue hace menos de un minuto. */
    public void acquireSend(String email) {
        Instant now = clock.instant();
        purgeIfLarge(now);
        boolean[] allowed = {true};
        lastSend.compute(email, (key, previous) -> {
            if (previous != null && now.isBefore(previous.plus(SEND_INTERVAL))) {
                allowed[0] = false;
                return previous;
            }
            return now;
        });
        if (!allowed[0]) {
            throw new TooManyRequestsException("Espera un minuto antes de pedir otro código");
        }
    }

    /** Falla con 429 si el correo acumuló demasiados códigos incorrectos recientes. */
    public void checkNotLocked(String email) {
        Failures current = failures.get(email);
        if (current != null && current.isLocked(clock.instant())) {
            throw new TooManyRequestsException("Demasiados intentos. Espera unos minutos y pide un código nuevo");
        }
    }

    public void recordFailure(String email) {
        Instant now = clock.instant();
        failures.compute(email, (key, current) -> current == null || current.expired(now)
                ? new Failures(1, now) : new Failures(current.count() + 1, current.since()));
    }

    /** Tras un restablecimiento correcto se olvidan los intentos fallidos. */
    public void clear(String email) {
        failures.remove(email);
    }

    private void purgeIfLarge(Instant now) {
        if (lastSend.size() > PURGE_THRESHOLD) {
            lastSend.values().removeIf(sent -> now.isAfter(sent.plus(SEND_INTERVAL)));
        }
        if (failures.size() > PURGE_THRESHOLD) {
            failures.values().removeIf(entry -> entry.expired(now));
        }
    }

    private record Failures(int count, Instant since) {
        boolean expired(Instant now) {
            return now.isAfter(since.plus(FAILURE_WINDOW));
        }

        boolean isLocked(Instant now) {
            return count >= MAX_FAILURES && !expired(now);
        }
    }
}

package com.kodika.kodikalab.auth;

import com.kodika.kodikalab.common.exception.TooManyRequestsException;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Límites del restablecimiento de contraseña, por correo y en memoria (sin tablas nuevas):
 * <ul>
 *   <li>Envío: como mucho un correo por minuto, para que nadie use la API como emisor de correo masivo.</li>
 *   <li>Verificación: como mucho 5 intentos en 15 minutos por correo. El intento se <b>reserva antes</b> de comprobar
 *       el código y se devuelve solo si el código era correcto; así las peticiones simultáneas no pueden evaluar más
 *       códigos que el cupo. Es lo que hace viable un código de solo 6 dígitos.</li>
 *   <li>Solicitud previa: un código solo se acepta si se pidió un envío para ese correo en los últimos 20 minutos
 *       (lo que dura un código); sin eso, el código de una cuenta activa existiría aunque su titular nunca lo pidiera.</li>
 * </ul>
 * Los límites se aplican igual a correos registrados y no registrados, para no revelar qué cuentas existen. La clave es
 * el correo plegado como lo hace la base de datos al comparar sin distinguir mayúsculas (por ejemplo, "ı" cuenta como
 * "i"), de modo que las variantes de escritura de un correo comparten el mismo cupo. Al ser en memoria se reinician con
 * la aplicación (los códigos pendientes hay que pedirlos de nuevo) y no se comparten entre instancias: es suficiente
 * para el monolito actual.
 */
@Component
public class PasswordResetAttemptLimiter {
    static final Duration SEND_INTERVAL = Duration.ofSeconds(60);
    static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);
    static final Duration CODE_VALIDITY = Duration.ofMinutes(20);
    static final int MAX_ATTEMPTS = 5;
    private static final int PURGE_THRESHOLD = 10_000;
    private static final Duration PURGE_EVERY = Duration.ofMinutes(1);

    private final Clock clock;
    private final Map<String, Instant> lastSend = new ConcurrentHashMap<>();
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();
    private volatile Instant lastPurge;

    public PasswordResetAttemptLimiter() {
        this(Clock.systemUTC());
    }

    PasswordResetAttemptLimiter(Clock clock) {
        this.clock = clock;
        this.lastPurge = clock.instant();
    }

    /** Registra un envío o falla con 429 si el último fue hace menos de un minuto. */
    public void acquireSend(String email) {
        Instant now = clock.instant();
        purgeIfLarge(now);
        boolean[] allowed = {true};
        lastSend.compute(key(email), (key, previous) -> {
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

    /** Indica si se pidió un envío para este correo hace menos de lo que dura un código. */
    public boolean codeRequestedRecently(String email) {
        Instant sent = lastSend.get(key(email));
        return sent != null && !clock.instant().isAfter(sent.plus(CODE_VALIDITY));
    }

    /**
     * Reserva un intento de verificación de forma atómica o falla con 429 si el cupo está agotado. Devuelve la ventana
     * a la que pertenece para poder devolver el intento con {@link #refundAttempt}.
     */
    public Instant reserveAttempt(String email) {
        Instant now = clock.instant();
        purgeIfLarge(now);
        Instant[] window = new Instant[1];
        boolean[] exhausted = {false};
        attempts.compute(key(email), (key, current) -> {
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
            throw new TooManyRequestsException("Demasiados intentos. Espera unos minutos y pide un código nuevo");
        }
        return window[0];
    }

    /** Devuelve un intento reservado cuando el código era correcto (solo si la ventana sigue siendo la misma). */
    public void refundAttempt(String email, Instant window) {
        attempts.computeIfPresent(key(email), (key, current) ->
                current.since().equals(window) && current.count() > 0
                        ? new Attempts(current.count() - 1, current.since()) : current);
    }

    /** Tras un restablecimiento correcto se olvidan los intentos y la solicitud del código. */
    public void clear(String email) {
        String key = key(email);
        attempts.remove(key);
        lastSend.remove(key);
    }

    /**
     * Pliega el correo como lo hace la comparación sin distinguir mayúsculas de la base de datos (mayúsculas y luego
     * minúsculas), para que "cıla@x.com" y "cila@x.com" no tengan cupos distintos si apuntan a la misma cuenta.
     */
    static String key(String email) {
        return Normalizer.normalize(email.strip(), Normalizer.Form.NFKC)
                .toUpperCase(Locale.ROOT).toLowerCase(Locale.ROOT);
    }

    int trackedAttempts() {
        return attempts.size();
    }

    /** Como mucho una vez por minuto, para que un atacante no convierta cada petición en un barrido completo. */
    private void purgeIfLarge(Instant now) {
        if (lastSend.size() <= PURGE_THRESHOLD && attempts.size() <= PURGE_THRESHOLD) {
            return;
        }
        if (now.isBefore(lastPurge.plus(PURGE_EVERY))) {
            return;
        }
        lastPurge = now;
        lastSend.values().removeIf(sent -> now.isAfter(sent.plus(CODE_VALIDITY)));
        attempts.values().removeIf(entry -> entry.expired(now));
    }

    private record Attempts(int count, Instant since) {
        boolean expired(Instant now) {
            return now.isAfter(since.plus(FAILURE_WINDOW));
        }
    }
}

package com.kodika.kodikalab.agent.core;

import com.kodika.kodikalab.common.exception.TooManyRequestsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Límite de mensajes por usuario, para proteger la cuota del proveedor. En memoria: se reinicia con la aplicación y no
 * se comparte entre instancias, suficiente para el monolito actual.
 */
@Component
public class AgentRateLimiter {
    public static final int MAX_PER_WINDOW = 10;
    static final Duration WINDOW = Duration.ofMinutes(1);

    private final Clock clock;
    private final Map<Integer, Usage> usage = new ConcurrentHashMap<>();

    @Autowired
    public AgentRateLimiter() {
        this(Clock.systemUTC());
    }

    AgentRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public void acquire(Integer userId) {
        Instant now = clock.instant();
        Usage updated = usage.compute(userId, (id, current) ->
                current == null || !now.isBefore(current.since().plus(WINDOW)) ? new Usage(1, now)
                        : new Usage(Math.min(current.count() + 1, MAX_PER_WINDOW + 1), current.since()));
        if (updated.count() > MAX_PER_WINDOW) {
            throw new TooManyRequestsException("Alcanzaste el límite de mensajes. Espera un minuto");
        }
    }

    private record Usage(int count, Instant since) {
    }
}

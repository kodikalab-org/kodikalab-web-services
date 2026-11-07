package com.kodika.kodikalab.problems.codeforces;

import com.kodika.kodikalab.profiles.practitioner.integration.CodeforcesProblem;
import com.kodika.kodikalab.profiles.practitioner.integration.CodeforcesProblemClient;
import com.kodika.kodikalab.profiles.practitioner.integration.CodeforcesUnavailableException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Copia en memoria del problemset público de Codeforces (miles de problemas, varios MB): se descarga la primera vez
 * que se necesita y se renueva de forma perezosa al vencer el TTL. Si la renovación falla y hay una copia vencida, se
 * sigue usando esa copia y no se reintenta hasta pasado {@link #RETRY_AFTER_FAILURE}.
 */
@Component
public class CodeforcesProblemCatalog {
    static final Duration RETRY_AFTER_FAILURE = Duration.ofMinutes(1);

    private final CodeforcesProblemClient client;
    private final Duration ttl;
    private final Clock clock;
    private volatile Snapshot snapshot;
    private Instant retryNotBefore = Instant.MIN;

    @Autowired
    public CodeforcesProblemCatalog(CodeforcesProblemClient client,
                                    @Value("${kodikalab.integrations.codeforces.catalog-ttl-hours:6}") long ttlHours) {
        this(client, Duration.ofHours(ttlHours), Clock.systemUTC());
    }

    CodeforcesProblemCatalog(CodeforcesProblemClient client, Duration ttl, Clock clock) {
        this.client = client;
        this.ttl = ttl;
        this.clock = clock;
    }

    /**
     * Problemas del problemset, del más reciente al más antiguo (orden de Codeforces).
     *
     * @throws CodeforcesUnavailableException si no hay copia y Codeforces no responde
     */
    public List<CodeforcesProblem> problems() {
        Snapshot current = snapshot;
        if (current != null && fresh(current)) {
            return current.problems();
        }
        synchronized (this) {
            current = snapshot;
            if (current != null && fresh(current)) {
                return current.problems();
            }
            Instant now = clock.instant();
            if (current != null && now.isBefore(retryNotBefore)) {
                return current.problems();
            }
            try {
                snapshot = new Snapshot(client.listProblems(), now);
                return snapshot.problems();
            } catch (CodeforcesUnavailableException exception) {
                if (current == null) {
                    throw exception;
                }
                retryNotBefore = now.plus(RETRY_AFTER_FAILURE);
                return current.problems();
            }
        }
    }

    /**
     * El problema en el problemset (descarga o renueva la copia si hace falta).
     *
     * @throws CodeforcesUnavailableException si no hay copia y Codeforces no responde
     */
    public Optional<CodeforcesProblem> find(int contestId, String index) {
        String wanted = index.toUpperCase(Locale.ROOT);
        return problems().stream()
                .filter(problem -> problem.contestId() == contestId && problem.index().equals(wanted))
                .findFirst();
    }

    /** El problema en la copia actual, aunque esté vencida; nunca descarga nada. */
    public Optional<CodeforcesProblem> peek(int contestId, String index) {
        Snapshot current = snapshot;
        if (current == null) {
            return Optional.empty();
        }
        String wanted = index.toUpperCase(Locale.ROOT);
        return current.problems().stream()
                .filter(problem -> problem.contestId() == contestId && problem.index().equals(wanted))
                .findFirst();
    }

    private boolean fresh(Snapshot current) {
        return clock.instant().isBefore(current.loadedAt().plus(ttl));
    }

    private record Snapshot(List<CodeforcesProblem> problems, Instant loadedAt) {
    }
}

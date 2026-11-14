package com.kodika.kodikalab.competitions.problemresolution;

import java.util.Locale;

/** Valores de resolucion_problema.veredicto. */
public enum Verdict {
    PENDIENTE,
    ACCEPTED,
    WRONG_ANSWER,
    TLE,
    MLE,
    ERROR,
    COMPILATION_ERROR,
    RUNTIME_ERROR,
    /** El envío existe en la plataforma pero aún se está juzgando: no resuelve ni penaliza. */
    EN_EVALUACION,
    /** Veredicto real de la plataforma sin equivalente propio (p. ej. PRESENTATION_ERROR). */
    OTRO;

    /** Sin veredicto definitivo todavía: no cuenta como resuelto ni como intento fallido. */
    public boolean isPending() {
        return this == PENDIENTE || this == EN_EVALUACION;
    }

    /** Intento ya juzgado que no fue aceptado (penaliza en la clasificación). */
    public boolean isFailure() {
        return this != ACCEPTED && !isPending();
    }

    /**
     * Mapa explícito desde el veredicto de la API de Codeforces ({@code user.status}). Ausente o {@code TESTING}
     * significa que el envío aún se juzga; cualquier otro valor no listado es {@link #OTRO}.
     */
    public static Verdict fromCodeforces(String codeforcesVerdict) {
        if (codeforcesVerdict == null || codeforcesVerdict.isBlank()) {
            return EN_EVALUACION;
        }
        return switch (codeforcesVerdict.strip().toUpperCase(Locale.ROOT)) {
            case "OK" -> ACCEPTED;
            case "WRONG_ANSWER" -> WRONG_ANSWER;
            case "TIME_LIMIT_EXCEEDED" -> TLE;
            case "MEMORY_LIMIT_EXCEEDED" -> MLE;
            case "COMPILATION_ERROR" -> COMPILATION_ERROR;
            case "RUNTIME_ERROR" -> RUNTIME_ERROR;
            case "TESTING" -> EN_EVALUACION;
            default -> OTRO;
        };
    }
}

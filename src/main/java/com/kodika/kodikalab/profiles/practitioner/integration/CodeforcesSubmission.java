package com.kodika.kodikalab.profiles.practitioner.integration;

/**
 * Envío de un usuario según {@code user.status}. {@code verdict} es el texto de Codeforces ({@code OK},
 * {@code WRONG_ANSWER}...) o {@code null} mientras se juzga. {@code passedTestCount} es el número de pruebas
 * superadas (en un veredicto fallido, la prueba que falló es la siguiente). Los campos opcionales pueden ser
 * {@code null} si Codeforces no los informa.
 */
public record CodeforcesSubmission(Long id, long creationTimeSeconds, Integer contestId, String index,
                                   Integer rating, String verdict, String programmingLanguage,
                                   Integer timeConsumedMillis, Long memoryConsumedBytes, Integer passedTestCount) {
    /** Constructor reducido: solo lo que usan las estadísticas. */
    public CodeforcesSubmission(long creationTimeSeconds, Integer contestId, String index, Integer rating,
                                String verdict) {
        this(null, creationTimeSeconds, contestId, index, rating, verdict, null, null, null, null);
    }
}

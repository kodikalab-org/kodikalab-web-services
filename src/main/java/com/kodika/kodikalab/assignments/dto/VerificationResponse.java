package com.kodika.kodikalab.assignments.dto;

import com.kodika.kodikalab.assignments.AssignmentStatus;
import com.kodika.kodikalab.competitions.problemresolution.Verdict;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Resultado de verificar un problema asignado leyendo los últimos envíos del practicante en Codeforces.
 * {@code found} indica si apareció al menos un envío de ese problema en la ventana consultada (aunque sea anterior a
 * la competencia). {@code status} es el estado personal resultante. {@code fileStored} indica si el archivo adjunto
 * quedó guardado (requiere un envío válido del problema).
 */
public record VerificationResponse(Integer competitionProblemId, boolean found, AssignmentStatus status,
                                   boolean fileStored, List<Submission> submissions) {
    public VerificationResponse {
        submissions = List.copyOf(submissions);
    }

    /** Un envío del problema. Los anteriores al inicio de la competencia no se importan. */
    public record Submission(Long externalId, Verdict verdict, String language, Integer timeMs, Integer memoryKb,
                             Integer passedTests, OffsetDateTime submittedAt, String url, boolean imported,
                             boolean beforeCompetition) {
    }
}

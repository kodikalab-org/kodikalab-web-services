package com.kodika.kodikalab.assignments;

import com.kodika.kodikalab.assignments.dto.PlatformSubmissionsResponse;
import com.kodika.kodikalab.assignments.dto.SubmissionFile;
import com.kodika.kodikalab.assignments.dto.VerificationResponse;

/**
 * Verificación de problemas asignados con solo lectura: KodikaLab nunca envía soluciones; lee (GET) los últimos envíos
 * del handle de Codeforces del practicante y registra el veredicto real.
 */
public interface SubmissionVerificationService {
    int DEFAULT_COUNT = 10;
    int MAX_COUNT = 100;

    /**
     * Importa, de forma idempotente, los envíos recientes del practicante para el problema asignado. {@code file}
     * y {@code count} son opcionales ({@code count} por defecto {@value #DEFAULT_COUNT}, máximo {@value #MAX_COUNT}).
     */
    VerificationResponse verify(Integer competitionProblemId, SubmissionFile file, Integer count);

    /** Últimos envíos de la cuenta de Codeforces del practicante, marcando los que corresponden al problema. */
    PlatformSubmissionsResponse platformSubmissions(Integer competitionProblemId, Integer count);
}

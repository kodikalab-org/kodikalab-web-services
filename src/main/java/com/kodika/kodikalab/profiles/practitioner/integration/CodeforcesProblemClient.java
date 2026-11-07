package com.kodika.kodikalab.profiles.practitioner.integration;

import java.util.List;
import java.util.Optional;

/** Lectura de problemas de la API pública de Codeforces. Los fallos de red o del servicio son {@link CodeforcesUnavailableException}. */
public interface CodeforcesProblemClient {
    /** El problema {@code contestId + index}; vacío si el concurso o el índice no existen. */
    Optional<CodeforcesProblem> findProblem(int contestId, String index);

    /** Todo el problemset público (miles de problemas; el llamador debe cachearlo). */
    List<CodeforcesProblem> listProblems();
}

package com.kodika.kodikalab.problems.problem;

import com.kodika.kodikalab.problems.problem.dto.CreateProblemRequest;
import com.kodika.kodikalab.problems.problem.dto.ProblemPageResponse;
import com.kodika.kodikalab.problems.problem.dto.ProblemResponse;
import com.kodika.kodikalab.problems.problem.dto.ProblemSearch;
import com.kodika.kodikalab.problems.problem.dto.ProblemSummary;
import java.util.Collection;
import java.util.List;

/** Servicio del catálogo de problemas (problema). */
public interface ProblemService {
    /** Un coach activo registra un problema en el catálogo, con sus temas (se crean los que no existan). */
    ProblemResponse create(CreateProblemRequest request);

    /** Búsqueda paginada del catálogo para cualquier cuenta activa. */
    ProblemPageResponse search(ProblemSearch search);

    /** Datos de catálogo de los problemas indicados; los identificadores inexistentes no aparecen. */
    List<ProblemSummary> findSummariesByIds(Collection<Integer> ids);
}

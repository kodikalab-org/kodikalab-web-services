package com.kodika.kodikalab.problems.problem;

import com.kodika.kodikalab.problems.problem.dto.CreateProblemRequest;
import com.kodika.kodikalab.problems.problem.dto.ProblemPageResponse;
import com.kodika.kodikalab.problems.problem.dto.ProblemResponse;
import com.kodika.kodikalab.problems.problem.dto.ProblemSearch;
import com.kodika.kodikalab.problems.problem.dto.ProblemSummary;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Servicio del catálogo de problemas (problema). */
public interface ProblemService {
    /** Un coach activo registra un problema en el catálogo, con sus temas (se crean los que no existan). */
    ProblemResponse create(CreateProblemRequest request);

    /** Búsqueda paginada del catálogo para cualquier cuenta activa. */
    ProblemPageResponse search(ProblemSearch search);

    /** Datos de catálogo de los problemas indicados; los identificadores inexistentes no aparecen. */
    List<ProblemSummary> findSummariesByIds(Collection<Integer> ids);

    /**
     * El problema ya registrado con ese código de origen en la plataforma (sin distinguir mayúsculas) o, si no hay
     * ninguno, con esa URL. Es una consulta interna: no valida la cuenta.
     */
    Optional<ProblemResponse> findExisting(SourcePlatform platform, String sourceCode, String url);

    /**
     * Identificadores locales de los códigos de origen indicados (clave: código en mayúsculas, sin el prefijo
     * {@code CF-} que usaba el registro manual); los códigos no registrados no aparecen.
     */
    Map<String, Integer> findIdsBySourceCodes(SourcePlatform platform, Collection<String> sourceCodes);
}

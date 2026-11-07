package com.kodika.kodikalab.problems.codeforces;

import com.kodika.kodikalab.problems.codeforces.dto.CodeforcesProblemSuggestion;
import com.kodika.kodikalab.problems.codeforces.dto.ImportCodeforcesProblemRequest;
import com.kodika.kodikalab.problems.codeforces.dto.ImportCodeforcesProblemResponse;
import java.util.List;

/** Alta de problemas del catálogo a partir de Codeforces (solo coach activo). */
public interface CodeforcesProblemService {
    /**
     * Registra en el catálogo el problema de Codeforces indicado por código o URL. Es idempotente: si ya existe
     * responde {@code created=false} sin consultar Codeforces.
     */
    ImportCodeforcesProblemResponse importProblem(ImportCodeforcesProblemRequest request);

    /**
     * Busca en el problemset de Codeforces por prefijo del código o por título (sin distinguir mayúsculas).
     *
     * @param limit máximo de resultados; {@code null} usa el valor por defecto
     */
    List<CodeforcesProblemSuggestion> search(String query, Integer limit);
}

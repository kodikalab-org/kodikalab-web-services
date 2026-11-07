package com.kodika.kodikalab.problems.codeforces;

import com.kodika.kodikalab.common.exception.FieldConflictException;
import com.kodika.kodikalab.common.exception.FieldValidationException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.problems.codeforces.dto.CodeforcesProblemSuggestion;
import com.kodika.kodikalab.problems.codeforces.dto.ImportCodeforcesProblemRequest;
import com.kodika.kodikalab.problems.codeforces.dto.ImportCodeforcesProblemResponse;
import com.kodika.kodikalab.problems.problem.ProblemService;
import com.kodika.kodikalab.problems.problem.SourcePlatform;
import com.kodika.kodikalab.problems.problem.dto.CreateProblemRequest;
import com.kodika.kodikalab.problems.problem.dto.ProblemResponse;
import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.profiles.practitioner.integration.CodeforcesProblem;
import com.kodika.kodikalab.profiles.practitioner.integration.CodeforcesProblemClient;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Esta clase no es transaccional a propósito: la llamada HTTP a Codeforces no debe retener una conexión de base de
 * datos. Cada operación sobre el catálogo ({@link ProblemService}) abre y cierra su propia transacción.
 */
@Service
public class CodeforcesProblemServiceImpl implements CodeforcesProblemService {
    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 50;
    static final int MIN_QUERY = 2;
    static final int MAX_QUERY = 100;
    /** La API de Codeforces no publica límites: se usan estos valores y el coach puede ajustarlos después. */
    static final int IMPORTED_TIME_LIMIT_MS = 2000;
    static final int IMPORTED_MEMORY_LIMIT_MB = 256;
    private static final int MAX_TOPICS = 20;

    private final CodeforcesProblemClient client;
    private final CodeforcesProblemCatalog catalog;
    private final ProblemService problemService;
    private final CurrentUserResolver currentUserResolver;
    /** El esquema no tiene restricción única de plataforma+código: este cerrojo evita duplicados dentro del servidor. */
    private final Object importLock = new Object();

    public CodeforcesProblemServiceImpl(CodeforcesProblemClient client, CodeforcesProblemCatalog catalog,
                                        ProblemService problemService, CurrentUserResolver currentUserResolver) {
        this.client = client;
        this.catalog = catalog;
        this.problemService = problemService;
        this.currentUserResolver = currentUserResolver;
    }

    @Override
    public ImportCodeforcesProblemResponse importProblem(ImportCodeforcesProblemRequest request) {
        requireCoach();
        CodeforcesProblemCode code = CodeforcesProblemCode.parse(request == null ? null : request.code());

        Optional<ProblemResponse> existing = findExisting(code);
        if (existing.isPresent()) {
            return new ImportCodeforcesProblemResponse(false, existing.get());
        }
        CodeforcesProblem remote = client.findProblem(code.contestId(), code.index())
                .orElseThrow(() -> new NotFoundException("El problema no existe en Codeforces"));
        // contest.standings no siempre trae la dificultad: se completa con la copia del problemset si ya está en memoria.
        if (remote.rating() == null) {
            Optional<CodeforcesProblem> known = catalog.peek(code.contestId(), code.index());
            if (known.isPresent() && known.get().rating() != null) {
                remote = new CodeforcesProblem(remote.contestId(), remote.index(), remote.name(),
                        known.get().rating(), remote.tags());
            }
        }
        CreateProblemRequest create = new CreateProblemRequest(remote.name(), code.url(), SourcePlatform.CODEFORCES,
                code.sourceCode(), remote.rating() == null ? null : String.valueOf(remote.rating()),
                IMPORTED_TIME_LIMIT_MS, IMPORTED_MEMORY_LIMIT_MB, topics(remote));

        synchronized (importLock) {
            existing = findExisting(code);
            if (existing.isPresent()) {
                return new ImportCodeforcesProblemResponse(false, existing.get());
            }
            try {
                return new ImportCodeforcesProblemResponse(true, problemService.create(create));
            } catch (FieldConflictException | DataIntegrityViolationException exception) {
                // Otra instancia del servidor lo registró entre la consulta y el alta: es el mismo resultado.
                Optional<ProblemResponse> raced = findExisting(code);
                if (raced.isPresent()) {
                    return new ImportCodeforcesProblemResponse(false, raced.get());
                }
                throw exception;
            }
        }
    }

    @Override
    public List<CodeforcesProblemSuggestion> search(String query, Integer limit) {
        requireCoach();
        String text = query == null ? "" : query.strip();
        int max = limit == null ? DEFAULT_LIMIT : limit;
        Map<String, String> errors = new LinkedHashMap<>();
        if (text.length() < MIN_QUERY || text.length() > MAX_QUERY) {
            errors.put("q", "La búsqueda debe tener entre " + MIN_QUERY + " y " + MAX_QUERY + " caracteres");
        }
        if (max < 1 || max > MAX_LIMIT) {
            errors.put("limit", "El máximo de resultados debe estar entre 1 y " + MAX_LIMIT);
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException("Los criterios de búsqueda deben corregirse", errors);
        }

        String needle = text.toLowerCase(Locale.ROOT);
        List<CodeforcesProblem> byCode = new ArrayList<>();
        List<CodeforcesProblem> byTitle = new ArrayList<>();
        for (CodeforcesProblem problem : catalog.problems()) {
            if (problem.code().toLowerCase(Locale.ROOT).startsWith(needle)) {
                byCode.add(problem);
            } else if (problem.name().toLowerCase(Locale.ROOT).contains(needle)) {
                byTitle.add(problem);
            }
            if (byCode.size() >= max) {
                break;
            }
        }
        List<CodeforcesProblem> matches = new ArrayList<>(byCode);
        matches.addAll(byTitle);
        if (matches.size() > max) {
            matches = matches.subList(0, max);
        }

        Map<String, Integer> imported = problemService.findIdsBySourceCodes(SourcePlatform.CODEFORCES,
                matches.stream().map(CodeforcesProblem::code).toList());
        return matches.stream().map(problem -> new CodeforcesProblemSuggestion(problem.code(), problem.name(),
                problem.rating() == null ? null : String.valueOf(problem.rating()), problem.tags(),
                new CodeforcesProblemCode(problem.contestId(), problem.index()).url(),
                imported.get(problem.code()))).toList();
    }

    private Optional<ProblemResponse> findExisting(CodeforcesProblemCode code) {
        return problemService.findExisting(SourcePlatform.CODEFORCES, code.sourceCode(), code.url());
    }

    private static List<String> topics(CodeforcesProblem problem) {
        return problem.tags().stream().map(String::strip).filter(tag -> !tag.isEmpty()).limit(MAX_TOPICS).toList();
    }

    private void requireCoach() {
        User user = currentUserResolver.currentUser();
        if (user.getStatus() != UserStatus.ACTIVO) {
            throw new ForbiddenException("La cuenta no está activa");
        }
        if (user.getRole() != Role.COACH) {
            throw new ForbiddenException("Solo un coach activo puede importar problemas de Codeforces");
        }
    }
}

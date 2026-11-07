package com.kodika.kodikalab.problems;

import com.kodika.kodikalab.problems.codeforces.CodeforcesProblemService;
import com.kodika.kodikalab.problems.codeforces.dto.CodeforcesProblemSuggestion;
import com.kodika.kodikalab.problems.codeforces.dto.ImportCodeforcesProblemRequest;
import com.kodika.kodikalab.problems.codeforces.dto.ImportCodeforcesProblemResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Importación y búsqueda de problemas de Codeforces en {@code /api/problems/codeforces} (solo coach). */
@RestController
@RequestMapping("/problems/codeforces")
@Tag(name = "Problemas", description = "Catálogo de problemas y búsqueda (US-07).")
public class CodeforcesProblemController {
    private final CodeforcesProblemService service;

    public CodeforcesProblemController(CodeforcesProblemService service) {
        this.service = service;
    }

    /** 201 si se registró; 200 si el problema ya estaba en el catálogo. */
    @PostMapping("/import")
    public ResponseEntity<ImportCodeforcesProblemResponse> importProblem(
            @RequestBody ImportCodeforcesProblemRequest request) {
        ImportCodeforcesProblemResponse response = service.importProblem(request);
        return ResponseEntity.status(response.created() ? HttpStatus.CREATED : HttpStatus.OK).body(response);
    }

    @GetMapping("/search")
    public List<CodeforcesProblemSuggestion> search(@RequestParam(name = "q", required = false) String query,
                                                    @RequestParam(required = false) Integer limit) {
        return service.search(query, limit);
    }
}

package com.kodika.kodikalab.problems;

import com.kodika.kodikalab.common.exception.FieldValidationException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.problems.codeforces.CodeforcesProblemService;
import com.kodika.kodikalab.problems.codeforces.dto.CodeforcesProblemSuggestion;
import com.kodika.kodikalab.problems.codeforces.dto.ImportCodeforcesProblemRequest;
import com.kodika.kodikalab.problems.codeforces.dto.ImportCodeforcesProblemResponse;
import com.kodika.kodikalab.problems.problem.SourcePlatform;
import com.kodika.kodikalab.problems.problem.dto.ProblemResponse;
import com.kodika.kodikalab.profiles.practitioner.integration.CodeforcesUnavailableException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CodeforcesProblemControllerTests {
    static final String URL = "https://codeforces.com/problemset/problem/4/A";

    CodeforcesProblemService service;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(CodeforcesProblemService.class);
        mvc = MockMvcBuilders.standaloneSetup(new CodeforcesProblemController(service))
                .setControllerAdvice(new ProblemsExceptionHandler()).build();
    }

    private static ProblemResponse problem() {
        return new ProblemResponse(12, "Watermelon", URL, SourcePlatform.CODEFORCES, "4A", "800", 2000, 256,
                List.of(new ProblemResponse.TopicInfo(1, "math")));
    }

    private org.springframework.test.web.servlet.ResultActions importCode(String body) throws Exception {
        return mvc.perform(post("/problems/codeforces/import").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void importingANewProblemRespondsCreatedWithTheCatalogItem() throws Exception {
        when(service.importProblem(new ImportCodeforcesProblemRequest("4A")))
                .thenReturn(new ImportCodeforcesProblemResponse(true, problem()));

        importCode("{\"code\":\"4A\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created").value(true))
                .andExpect(jsonPath("$.problem.id").value(12))
                .andExpect(jsonPath("$.problem.title").value("Watermelon"))
                .andExpect(jsonPath("$.problem.url").value(URL))
                .andExpect(jsonPath("$.problem.sourcePlatform").value("CODEFORCES"))
                .andExpect(jsonPath("$.problem.sourceCode").value("4A"))
                .andExpect(jsonPath("$.problem.difficultyRating").value("800"))
                .andExpect(jsonPath("$.problem.timeLimitMs").value(2000))
                .andExpect(jsonPath("$.problem.memoryLimitMb").value(256))
                .andExpect(jsonPath("$.problem.topics[0].id").value(1))
                .andExpect(jsonPath("$.problem.topics[0].name").value("math"));
    }

    @Test
    void importingAKnownProblemRespondsOkWithCreatedFalse() throws Exception {
        when(service.importProblem(any())).thenReturn(new ImportCodeforcesProblemResponse(false, problem()));

        importCode("{\"code\":\"https://codeforces.com/problemset/problem/4/A\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false))
                .andExpect(jsonPath("$.problem.id").value(12));
        verify(service).importProblem(new ImportCodeforcesProblemRequest(URL));
    }

    @Test
    void aMissingOrNonTextCodeArrivesAsNullOrIsRejectedByShape() throws Exception {
        when(service.importProblem(new ImportCodeforcesProblemRequest(null)))
                .thenThrow(new FieldValidationException("El código del problema no es válido",
                        Map.of("code", "Indique el código del problema")));

        importCode("{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.code").exists());
        importCode("{\"code\":5}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.code").exists());
        importCode("[]").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
        importCode("no es json").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
        verify(service, never()).importProblem(new ImportCodeforcesProblemRequest("5"));
    }

    @Test
    void invalidFormatIsBadRequestWithTheErrorOnCode() throws Exception {
        when(service.importProblem(any())).thenThrow(new FieldValidationException("El código del problema no es válido",
                Map.of("code", "Use el formato 1850A")));

        importCode("{\"code\":\"xyz\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El código del problema no es válido"))
                .andExpect(jsonPath("$.errors.code").value("Use el formato 1850A"));
    }

    @Test
    void unknownProblemIsNotFound() throws Exception {
        when(service.importProblem(any())).thenThrow(new NotFoundException("El problema no existe en Codeforces"));

        importCode("{\"code\":\"4Z\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("El problema no existe en Codeforces"));
    }

    @Test
    void codeforcesDownIsServiceUnavailableOnImportAndSearch() throws Exception {
        when(service.importProblem(any())).thenThrow(new CodeforcesUnavailableException());
        when(service.search(any(), any())).thenThrow(new CodeforcesUnavailableException());

        importCode("{\"code\":\"4A\"}")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message")
                        .value("Codeforces no está disponible temporalmente. Inténtelo de nuevo más tarde."));
        mvc.perform(get("/problems/codeforces/search").param("q", "watermelon"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void aNonCoachIsForbidden() throws Exception {
        when(service.importProblem(any())).thenThrow(new ForbiddenException("Solo un coach"));

        importCode("{\"code\":\"4A\"}").andExpect(status().isForbidden());
    }

    @Test
    void searchReturnsTheSuggestionsAsAPlainArray() throws Exception {
        when(service.search("watermelon", null)).thenReturn(List.of(
                new CodeforcesProblemSuggestion("4A", "Watermelon", "800", List.of("brute force", "math"), URL, 12),
                new CodeforcesProblemSuggestion("1234C", "Watermelon Returns", null, List.of(),
                        "https://codeforces.com/problemset/problem/1234/C", null)));

        mvc.perform(get("/problems/codeforces/search").param("q", "watermelon"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value("4A"))
                .andExpect(jsonPath("$[0].title").value("Watermelon"))
                .andExpect(jsonPath("$[0].difficultyRating").value("800"))
                .andExpect(jsonPath("$[0].topics[1]").value("math"))
                .andExpect(jsonPath("$[0].url").value(URL))
                .andExpect(jsonPath("$[0].problemId").value(12))
                .andExpect(jsonPath("$[1].difficultyRating").doesNotExist())
                .andExpect(jsonPath("$[1].problemId").doesNotExist());
    }

    @Test
    void searchPassesTheLimitAndReportsInvalidCriteriaAsBadRequest() throws Exception {
        when(service.search("1850", 5)).thenReturn(List.of());
        when(service.search("a", null)).thenThrow(new FieldValidationException("Los criterios de búsqueda deben corregirse",
                Map.of("q", "La búsqueda debe tener entre 2 y 100 caracteres")));

        mvc.perform(get("/problems/codeforces/search").param("q", "1850").param("limit", "5"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/problems/codeforces/search").param("q", "a"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.q").exists());
        mvc.perform(get("/problems/codeforces/search").param("q", "ab").param("limit", "mucho"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.limit").exists());
    }
}

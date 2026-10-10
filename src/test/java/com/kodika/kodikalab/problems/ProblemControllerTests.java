package com.kodika.kodikalab.problems;

import com.kodika.kodikalab.common.exception.ConflictException;
import com.kodika.kodikalab.common.exception.FieldConflictException;
import com.kodika.kodikalab.common.exception.FieldValidationException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.UnauthorizedException;
import com.kodika.kodikalab.problems.material.MaterialService;
import com.kodika.kodikalab.problems.problem.ProblemService;
import com.kodika.kodikalab.problems.problem.SourcePlatform;
import com.kodika.kodikalab.problems.problem.dto.CreateProblemRequest;
import com.kodika.kodikalab.problems.problem.dto.ProblemPageResponse;
import com.kodika.kodikalab.problems.problem.dto.ProblemResponse;
import com.kodika.kodikalab.problems.problem.dto.ProblemSearch;
import com.kodika.kodikalab.problems.problemtopic.ProblemTopicService;
import com.kodika.kodikalab.problems.topic.TopicService;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProblemControllerTests {
    static final String URL = "https://codeforces.com/problemset/problem/1/A";
    static final String VALID = "{\"title\":\"Theatre Square\",\"url\":\"" + URL + "\"}";

    ProblemService service;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(ProblemService.class);
        var controller = new ProblemController(service, mock(TopicService.class), mock(ProblemTopicService.class),
                mock(MaterialService.class));
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ProblemsExceptionHandler()).build();
    }

    private static ProblemResponse response() {
        return new ProblemResponse(7, "Theatre Square", URL, SourcePlatform.CODEFORCES, "1A", "1000", 1000, 256,
                List.of(new ProblemResponse.TopicInfo(3, "Matemática")));
    }

    @Test
    void createsAndReturnsTheStoredProblem() throws Exception {
        var request = new CreateProblemRequest("Theatre Square", URL, SourcePlatform.ATCODER, "1A", "1000", 2000, 512,
                List.of("Matemática", "Implementación"));
        when(service.create(request)).thenReturn(response());

        mvc.perform(post("/problems").contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"Theatre Square","url":"%s","sourcePlatform":"ATCODER","sourceCode":"1A",
                         "difficultyRating":"1000","timeLimitMs":2000,"memoryLimitMb":512,
                         "topics":["Matemática","Implementación"],"coachId":999}""".formatted(URL)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.topics[0].name").value("Matemática"))
                .andExpect(jsonPath("$.sourcePlatform").value("CODEFORCES"));
        verify(service).create(request);
    }

    @Test
    void omittedOptionalFieldsArriveAsNull() throws Exception {
        when(service.create(any())).thenReturn(response());

        mvc.perform(post("/problems").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated());
        verify(service).create(new CreateProblemRequest("Theatre Square", URL, null, null, null, null, null, null));
    }

    @Test
    void reportsEveryFieldWithAWrongTypeAndNeverCallsTheService() throws Exception {
        mvc.perform(post("/problems").contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":5,"url":true,"sourcePlatform":"LEETCODE","sourceCode":1,"difficultyRating":2,
                         "timeLimitMs":"1000","memoryLimitMb":1.5,"topics":["ok",3,null]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.errors.url").exists())
                .andExpect(jsonPath("$.errors.sourcePlatform").exists())
                .andExpect(jsonPath("$.errors.sourceCode").exists())
                .andExpect(jsonPath("$.errors.difficultyRating").exists())
                .andExpect(jsonPath("$.errors.timeLimitMs").exists())
                .andExpect(jsonPath("$.errors.memoryLimitMb").exists())
                .andExpect(jsonPath("$.errors['topics[1]']").exists())
                .andExpect(jsonPath("$.errors['topics[2]']").exists());
        verify(service, never()).create(any());
    }

    @Test
    void topicsMustBeAnArray() throws Exception {
        mvc.perform(post("/problems").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"T\",\"url\":\"" + URL + "\",\"topics\":\"Grafos\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.topics").exists());
    }

    @ParameterizedTest
    @MethodSource("malformedBodies")
    void malformedBodiesAreBadRequests(String body) throws Exception {
        mvc.perform(post("/problems").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
        verify(service, never()).create(any());
    }

    static Stream<String> malformedBodies() {
        return Stream.of("{\"title\":", "[]", "\"texto\"", "");
    }

    @ParameterizedTest
    @MethodSource("serviceFailures")
    void mapsServiceFailuresToStructuredErrors(RuntimeException failure, int code) throws Exception {
        when(service.create(any())).thenThrow(failure);

        mvc.perform(post("/problems").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().is(code)).andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.errors").exists());
    }

    static Stream<Arguments> serviceFailures() {
        return Stream.of(
                Arguments.of(new FieldValidationException("Corrija", Map.of("title", "Obligatorio")), 400),
                Arguments.of(new UnauthorizedException("Sin sesión"), 401),
                Arguments.of(new ForbiddenException("No autorizado"), 403),
                Arguments.of(new FieldConflictException("Ya existe", Map.of("url", "Repetida")), 409),
                Arguments.of(new ConflictException("Conflicto"), 409),
                Arguments.of(new DataIntegrityViolationException("uq_tema_nombre"), 409),
                Arguments.of(new DataAccessResourceFailureException("Internal SQL"), 503),
                Arguments.of(new IllegalStateException("boom"), 500));
    }

    // ---- GET /problems

    @Test
    void searchPassesTheCriteriaAndReturnsThePage() throws Exception {
        when(service.search(new ProblemSearch("theatre", 3, "1000", SourcePlatform.CODEFORCES, 2, 10)))
                .thenReturn(new ProblemPageResponse(List.of(response()), 2, 10, 21, 3));

        mvc.perform(get("/problems").param("q", "theatre").param("topicId", "3").param("difficulty", "1000")
                        .param("platform", "CODEFORCES").param("page", "2").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("Theatre Square"))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.totalItems").value(21))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void searchDefaultsToTheFirstPageOfTwenty() throws Exception {
        when(service.search(any())).thenReturn(new ProblemPageResponse(List.of(), 0, 20, 0, 0));

        mvc.perform(get("/problems")).andExpect(status().isOk());
        verify(service).search(new ProblemSearch(null, null, null, null, 0, 20));
    }

    @ParameterizedTest
    @MethodSource("badParameters")
    void searchRejectsParametersOfTheWrongType(String name, String value) throws Exception {
        mvc.perform(get("/problems").param(name, value))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors." + name).exists());
        verify(service, never()).search(any());
    }

    static Stream<Arguments> badParameters() {
        return Stream.of(Arguments.of("platform", "LEETCODE"), Arguments.of("topicId", "abc"),
                Arguments.of("page", "x"), Arguments.of("size", "1.5"));
    }

    @Test
    void searchMapsAuthorizationAndAvailabilityErrors() throws Exception {
        doThrow(new UnauthorizedException("Sin sesión")).when(service).search(any());
        mvc.perform(get("/problems")).andExpect(status().isUnauthorized());

        doThrow(new DataAccessResourceFailureException("Internal SQL")).when(service).search(any());
        mvc.perform(get("/problems")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("SQL"))));
    }
}

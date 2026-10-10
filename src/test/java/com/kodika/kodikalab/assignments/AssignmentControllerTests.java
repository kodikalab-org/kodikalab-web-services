package com.kodika.kodikalab.assignments;

import com.kodika.kodikalab.assignments.dto.AssignProblemsRequest;
import com.kodika.kodikalab.assignments.dto.AssignProblemsRequest.Item;
import com.kodika.kodikalab.assignments.dto.AssignProblemsResponse;
import com.kodika.kodikalab.assignments.dto.AssignProblemsResponse.AssignedItem;
import com.kodika.kodikalab.assignments.dto.AssignedProblemDetailResponse;
import com.kodika.kodikalab.assignments.dto.AssignedProblemResponse;
import com.kodika.kodikalab.assignments.dto.AssignedProblemsQuery;
import com.kodika.kodikalab.assignments.dto.AssignedProblemsResponse;
import com.kodika.kodikalab.common.exception.ConflictException;
import com.kodika.kodikalab.common.exception.FieldConflictException;
import com.kodika.kodikalab.common.exception.FieldValidationException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.common.exception.UnauthorizedException;
import com.kodika.kodikalab.competitions.competition.CompetitionAccessType;
import com.kodika.kodikalab.competitions.competition.CompetitionStatus;
import com.kodika.kodikalab.competitions.competition.PenaltyRule;
import com.kodika.kodikalab.competitions.problemresolution.Verdict;
import com.kodika.kodikalab.problems.problem.SourcePlatform;
import java.time.OffsetDateTime;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AssignmentControllerTests {
    static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-20T14:00:00-05:00");

    AssignmentService assignments;
    AssignedProblemsService assigned;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        assignments = mock(AssignmentService.class);
        assigned = mock(AssignedProblemsService.class);
        mvc = MockMvcBuilders.standaloneSetup(new AssignmentController(assignments, assigned))
                .setControllerAdvice(new AssignmentExceptionHandler()).build();
    }

    // ---- POST /problems/assign

    @Test
    void assignsAndReturnsTheCreatedRows() throws Exception {
        var request = new AssignProblemsRequest(5, List.of(new Item(11, "A", 100, "#FF0000"), new Item(12, null, null,
                null)));
        when(assignments.assign(request)).thenReturn(new AssignProblemsResponse(5, 1, List.of(
                new AssignedItem(100, 11, "Alfa", "A", 100, "#FF0000", NOW),
                new AssignedItem(101, 12, "Beta", "B", 1, "#FF0000", NOW))));

        mvc.perform(post("/problems/assign").contentType(MediaType.APPLICATION_JSON).content("""
                        {"competitionId":5,"teamId":999,"coachId":999,
                         "problems":[{"problemId":11,"letter":"A","score":100,"balloonColor":"#FF0000"},
                                     {"problemId":12}]}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.competitionId").value(5))
                .andExpect(jsonPath("$.teamId").value(1))
                .andExpect(jsonPath("$.assigned.length()").value(2))
                .andExpect(jsonPath("$.assigned[1].letter").value("B"))
                .andExpect(jsonPath("$.assigned[0].title").value("Alfa"));
        verify(assignments).assign(request);
    }

    @Test
    void reportsEveryFieldWithAWrongTypeAndNeverCallsTheService() throws Exception {
        mvc.perform(post("/problems/assign").contentType(MediaType.APPLICATION_JSON).content("""
                        {"competitionId":"5","problems":[{"problemId":"x","letter":5,"score":1.5,"balloonColor":true},
                                                          "texto", {"problemId":12}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.competitionId").exists())
                .andExpect(jsonPath("$.errors['problems[0].problemId']").exists())
                .andExpect(jsonPath("$.errors['problems[0].letter']").exists())
                .andExpect(jsonPath("$.errors['problems[0].score']").exists())
                .andExpect(jsonPath("$.errors['problems[0].balloonColor']").exists())
                .andExpect(jsonPath("$.errors['problems[1]']").exists());
        verify(assignments, never()).assign(any());
    }

    @Test
    void problemsMustBeAnArray() throws Exception {
        mvc.perform(post("/problems/assign").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"competitionId\":5,\"problems\":{\"problemId\":11}}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.problems").exists());
    }

    @ParameterizedTest
    @MethodSource("malformedBodies")
    void malformedBodiesAreBadRequests(String body) throws Exception {
        mvc.perform(post("/problems/assign").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
        verify(assignments, never()).assign(any());
    }

    static Stream<String> malformedBodies() {
        return Stream.of("{\"competitionId\":", "[]", "\"texto\"", "");
    }

    @ParameterizedTest
    @MethodSource("assignFailures")
    void mapsAssignFailuresToStructuredErrors(RuntimeException failure, int code, boolean hasErrors) throws Exception {
        when(assignments.assign(any())).thenThrow(failure);

        var result = mvc.perform(post("/problems/assign").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"competitionId\":5,\"problems\":[{\"problemId\":11}]}"))
                .andExpect(status().is(code)).andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.errors").exists());
        if (hasErrors) {
            result.andExpect(jsonPath("$.errors['problems[0].problemId']").exists());
        }
    }

    static Stream<Arguments> assignFailures() {
        Map<String, String> byField = Map.of("problems[0].problemId", "El problema ya está asignado");
        return Stream.of(
                Arguments.of(new FieldValidationException("Corrija", byField), 400, true),
                Arguments.of(new UnauthorizedException("Sin sesión"), 401, false),
                Arguments.of(new ForbiddenException("No autorizado"), 403, false),
                Arguments.of(new NotFoundException("La competencia no existe"), 404, false),
                Arguments.of(new FieldConflictException("Ya asignado", byField), 409, true),
                Arguments.of(new ConflictException("La competencia ya finalizó"), 409, false),
                Arguments.of(new DataIntegrityViolationException("uq_competencia_problema"), 409, false),
                Arguments.of(new DataAccessResourceFailureException("Internal SQL"), 503, false),
                Arguments.of(new IllegalStateException("boom"), 500, false));
    }

    // ---- GET /problems/assigned

    @Test
    void listPassesTheCriteriaAndReturnsTheItems() throws Exception {
        var query = new AssignedProblemsQuery(1, 5, CompetitionStatus.FINALIZADA, AssignmentStatus.RESUELTO, "alfa",
                "800", "title", "desc");
        when(assigned.list(query)).thenReturn(new AssignedProblemsResponse(1, 1, List.of(item())));

        mvc.perform(get("/problems/assigned").param("teamId", "1").param("competitionId", "5")
                        .param("competitionStatus", "FINALIZADA").param("status", "RESUELTO").param("q", "alfa")
                        .param("difficulty", "800").param("sort", "title").param("order", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamId").value(1))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].letter").value("A"))
                .andExpect(jsonPath("$.items[0].status").value("RESUELTO"))
                .andExpect(jsonPath("$.items[0].competition.name").value("Simulacro"))
                .andExpect(jsonPath("$.items[0].competition.accessKey").doesNotExist())
                .andExpect(jsonPath("$.items[0].problem.topics[0]").value("Grafos"));
    }

    @Test
    void listWithoutCriteriaPassesNulls() throws Exception {
        when(assigned.list(any())).thenReturn(new AssignedProblemsResponse(null, 0, List.of()));

        mvc.perform(get("/problems/assigned")).andExpect(status().isOk());
        verify(assigned).list(new AssignedProblemsQuery(null, null, null, null, null, null, null, null));
    }

    @ParameterizedTest
    @MethodSource("badParameters")
    void listRejectsParametersOfTheWrongType(String name, String value) throws Exception {
        mvc.perform(get("/problems/assigned").param(name, value))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors." + name).exists());
        verify(assigned, never()).list(any());
    }

    static Stream<Arguments> badParameters() {
        return Stream.of(Arguments.of("teamId", "abc"), Arguments.of("competitionId", "1.5"),
                Arguments.of("competitionStatus", "CERRADA"), Arguments.of("status", "TERMINADO"));
    }

    @Test
    void listMapsAccessAndAvailabilityErrors() throws Exception {
        doThrow(new UnauthorizedException("Sin sesión")).when(assigned).list(any());
        mvc.perform(get("/problems/assigned").param("teamId", "1")).andExpect(status().isUnauthorized());

        doThrow(new ForbiddenException("No pertenece a este equipo")).when(assigned).list(any());
        mvc.perform(get("/problems/assigned").param("teamId", "1")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("No pertenece a este equipo"));

        doThrow(new DataAccessResourceFailureException("Internal SQL")).when(assigned).list(any());
        mvc.perform(get("/problems/assigned").param("teamId", "1")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("No se pudo completar la consulta; puede intentarlo nuevamente"));
    }

    // ---- GET /problems/assigned/{id}

    @Test
    void detailReturnsTheAssignmentAndItsAttempts() throws Exception {
        when(assigned.detail(7)).thenReturn(new AssignedProblemDetailResponse(item(), List.of(
                new AssignedProblemResponse.AttemptInfo(901, Verdict.ACCEPTED, "Java", NOW, "https://evidence"))));

        mvc.perform(get("/problems/assigned/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignment.competitionProblemId").value(7))
                .andExpect(jsonPath("$.attempts[0].verdict").value("ACCEPTED"))
                .andExpect(jsonPath("$.attempts[0].language").value("Java"));
    }

    @Test
    void detailRejectsANonNumericIdAndMapsNotFound() throws Exception {
        mvc.perform(get("/problems/assigned/abc")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.competitionProblemId").exists());
        verify(assigned, never()).detail(anyInt());

        doThrow(new NotFoundException("La asignación no existe")).when(assigned).detail(99);
        mvc.perform(get("/problems/assigned/99")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("La asignación no existe"));
    }

    private static AssignedProblemResponse item() {
        return new AssignedProblemResponse(7, "A", 100, "#FF0000", NOW,
                new AssignedProblemResponse.CompetitionInfo(5, "Simulacro", CompetitionStatus.FINALIZADA, NOW,
                        NOW.plusHours(5), 300, PenaltyRule.ICPC_20_MIN, 60, CompetitionAccessType.PUBLICO_GRUPO),
                new AssignedProblemResponse.ProblemInfo(11, "Alfa", "https://codeforces.com/p/11",
                        SourcePlatform.CODEFORCES, "CF-A", "800", 1000, 256, List.of("Grafos")),
                AssignmentStatus.RESUELTO, 2,
                new AssignedProblemResponse.AttemptInfo(901, Verdict.ACCEPTED, "Java", NOW, "https://evidence"));
    }
}

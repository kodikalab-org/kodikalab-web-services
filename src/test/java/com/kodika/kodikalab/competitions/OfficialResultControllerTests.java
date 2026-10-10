package com.kodika.kodikalab.competitions;

import com.kodika.kodikalab.common.exception.ConflictException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.common.exception.UnauthorizedException;
import com.kodika.kodikalab.competitions.category.CategoryService;
import com.kodika.kodikalab.competitions.competition.CompetitionService;
import com.kodika.kodikalab.competitions.competitionproblem.CompetitionProblemService;
import com.kodika.kodikalab.competitions.officialresult.OfficialResultService;
import com.kodika.kodikalab.competitions.officialresult.OfficialResultStatus;
import com.kodika.kodikalab.competitions.officialresult.OfficialResultValidationException;
import com.kodika.kodikalab.competitions.officialresult.dto.OfficialResultRequest;
import com.kodika.kodikalab.competitions.officialresult.dto.OfficialResultResponse;
import com.kodika.kodikalab.competitions.problemresolution.ProblemResolutionService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.CannotCreateTransactionException;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OfficialResultControllerTests {
    OfficialResultService service;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(OfficialResultService.class);
        var controller = new CompetitionController(mock(CompetitionService.class), mock(CompetitionProblemService.class),
                mock(ProblemResolutionService.class), mock(CategoryService.class), service);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new CompetitionExceptionHandler()).build();
    }

    @Test
    void createsConfirmedResultWithStructuredResponse() throws Exception {
        when(service.create(2, new OfficialResultRequest(1, 3, true))).thenReturn(response());
        mvc.perform(post("/api/competitions/2/official-result").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"finalPosition\":1,\"solvedProblems\":3,\"confirm\":true,\"teamId\":999,\"userId\":999}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.teamId").value(1))
                .andExpect(jsonPath("$.competitionId").value(2))
                .andExpect(jsonPath("$.status").value("CONFIRMADO"))
                .andExpect(jsonPath("$.confirmedAt").exists());
        verify(service).create(2, new OfficialResultRequest(1, 3, true));
    }

    @Test
    void omittedConfirmationDoesNotPublishCompleteOrPartialData() throws Exception {
        mvc.perform(post("/competitions/2/official-result").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"solvedProblems\":2,\"status\":\"CONFIRMADO\"}"))
                .andExpect(status().isCreated());
        verify(service).create(2, new OfficialResultRequest(null, 2, false));
    }

    @Test
    void updatesPendingAndConfirmsExplicitly() throws Exception {
        when(service.update(2, new OfficialResultRequest(1, 3, true))).thenReturn(response());
        mvc.perform(put("/competitions/2/official-result").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"finalPosition\":1,\"solvedProblems\":3,\"confirm\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMADO"));
        verify(service).update(2, new OfficialResultRequest(1, 3, true));
    }

    @Test
    void consultsDetailAndTeamHistory() throws Exception {
        when(service.get(2)).thenReturn(response());
        when(service.history(1)).thenReturn(List.of(response()));
        mvc.perform(get("/competitions/2/official-result")).andExpect(status().isOk())
                .andExpect(jsonPath("$.finalPosition").value(1));
        mvc.perform(get("/competitions/teams/1/official-results")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].competitionId").value(2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"text", "2147483648"})
    void malformedIdentifiersDoNotCallService(String id) throws Exception {
        mvc.perform(get("/competitions/" + id + "/official-result")).andExpect(status().isBadRequest());
        mvc.perform(get("/competitions/teams/" + id + "/official-results")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    static Stream<Arguments> invalidFields() {
        return Stream.of(
                Arguments.of("{\"finalPosition\":1.5}", "finalPosition"),
                Arguments.of("{\"solvedProblems\":\"2\"}", "solvedProblems"),
                Arguments.of("{\"solvedProblems\":true}", "solvedProblems"),
                Arguments.of("{\"finalPosition\":2147483648}", "finalPosition"),
                Arguments.of("{\"confirm\":\"true\"}", "confirm"),
                Arguments.of("{\"confirm\":1}", "confirm"),
                Arguments.of("{\"confirm\":null}", "confirm"),
                Arguments.of("[]", "body"));
    }

    @ParameterizedTest
    @MethodSource("invalidFields")
    void rejectsInvalidJsonTypesWithoutCoercion(String body, String field) throws Exception {
        mvc.perform(post("/competitions/2/official-result").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors['" + field + "']").exists());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{"})
    void missingOrMalformedBodyIsRejected(String body) throws Exception {
        mvc.perform(post("/competitions/2/official-result").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void missingConfirmationFieldsAreIdentified() throws Exception {
        when(service.create(2, new OfficialResultRequest(null, null, true)))
                .thenThrow(new OfficialResultValidationException(Map.of("finalPosition", "Es obligatoria",
                        "solvedProblems", "Son obligatorios")));
        mvc.perform(post("/competitions/2/official-result").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirm\":true}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.finalPosition").exists())
                .andExpect(jsonPath("$.errors.solvedProblems").exists());
    }

    static Stream<Arguments> failures() {
        return Stream.of(Arguments.of(new UnauthorizedException("Perfil"), 401),
                Arguments.of(new ForbiddenException("Acceso denegado"), 403),
                Arguments.of(new NotFoundException("No existe"), 404),
                Arguments.of(new ConflictException("Duplicado"), 409),
                Arguments.of(new DataAccessResourceFailureException("SQL privado"), 503),
                Arguments.of(new CannotCreateTransactionException("Credenciales privadas"), 503),
                Arguments.of(new IllegalStateException("Detalle interno"), 500));
    }

    @ParameterizedTest
    @MethodSource("failures")
    void errorsDoNotPublishResultsOrInternalDetails(RuntimeException failure, int code) throws Exception {
        when(service.history(1)).thenThrow(failure);
        mvc.perform(get("/competitions/teams/1/official-results"))
                .andExpect(status().is(code)).andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.errors").isMap()).andExpect(jsonPath("$.finalPosition").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("privad"))));
    }

    private OfficialResultResponse response() {
        OffsetDateTime date = OffsetDateTime.parse("2026-10-01T18:00:00Z");
        return new OfficialResultResponse(3, 2, 1, "Competencia Prueba", date, 1, 3,
                OfficialResultStatus.CONFIRMADO, date, date);
    }
}

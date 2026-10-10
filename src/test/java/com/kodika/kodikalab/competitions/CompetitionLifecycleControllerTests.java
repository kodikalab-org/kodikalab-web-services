package com.kodika.kodikalab.competitions;

import com.kodika.kodikalab.common.exception.ConflictException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.common.exception.UnauthorizedException;
import com.kodika.kodikalab.competitions.category.CategoryService;
import com.kodika.kodikalab.competitions.competition.CompetitionAccessType;
import com.kodika.kodikalab.competitions.competition.CompetitionService;
import com.kodika.kodikalab.competitions.competition.CompetitionStatus;
import com.kodika.kodikalab.competitions.competition.CompetitionValidationException;
import com.kodika.kodikalab.competitions.competition.PenaltyRule;
import com.kodika.kodikalab.competitions.competition.dto.ChangeCompetitionStatusRequest;
import com.kodika.kodikalab.competitions.competition.dto.CompetitionListItem;
import com.kodika.kodikalab.competitions.competition.dto.CompetitionListResponse;
import com.kodika.kodikalab.competitions.competition.dto.CompetitionResponse;
import com.kodika.kodikalab.competitions.competitionproblem.CompetitionProblemService;
import com.kodika.kodikalab.competitions.officialresult.OfficialResultService;
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
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato HTTP del ciclo de vida de la competencia (PATCH del estado) y de su listado por equipo. */
class CompetitionLifecycleControllerTests {
    static final OffsetDateTime START = OffsetDateTime.parse("2026-10-20T14:00:00-05:00");

    CompetitionService service;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(CompetitionService.class);
        var controller = new CompetitionController(service, mock(CompetitionProblemService.class),
                mock(ProblemResolutionService.class), mock(CategoryService.class), mock(OfficialResultService.class));
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new CompetitionExceptionHandler()).build();
    }

    // ---- PATCH /competitions/{id}/status

    @Test
    void changesTheStatusAndNeverReturnsTheAccessKey() throws Exception {
        var started = new ChangeCompetitionStatusRequest(CompetitionStatus.EN_CURSO);
        when(service.changeStatus(7, started)).thenReturn(new CompetitionResponse(7, 1, "Privada", null,
                CompetitionAccessType.PRIVADO_PASS, PenaltyRule.ICPC_20_MIN, 300, 60, CompetitionStatus.EN_CURSO, START,
                START.plusHours(5)));

        mvc.perform(patch("/competitions/7/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EN_CURSO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.teamId").value(1))
                .andExpect(jsonPath("$.status").value("EN_CURSO"))
                .andExpect(jsonPath("$.accessKey").doesNotExist());
        verify(service).changeStatus(7, started);
    }

    @Test
    void aMissingStatusIsLeftToTheServiceToReject() throws Exception {
        when(service.changeStatus(eq(7), any())).thenThrow(
                new CompetitionValidationException(Map.of("status", "El nuevo estado es obligatorio")));

        mvc.perform(patch("/competitions/7/status").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.status").exists());
        verify(service).changeStatus(7, new ChangeCompetitionStatusRequest(null));
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void invalidBodiesAreBadRequestsThatNameTheFieldAndNeverReachTheService(String body, String field) throws Exception {
        mvc.perform(patch("/competitions/7/status").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.errors." + field).exists());
        verify(service, never()).changeStatus(any(), any());
    }

    static Stream<Arguments> invalidBodies() {
        return Stream.of(
                Arguments.of("{\"status\":1}", "status"),
                Arguments.of("{\"status\":true}", "status"),
                Arguments.of("{\"status\":\"CERRADA\"}", "status"),
                Arguments.of("{\"status\":\"en_curso\"}", "status"),
                Arguments.of("[]", "body"),
                Arguments.of("\"EN_CURSO\"", "body"));
    }

    @ParameterizedTest
    @MethodSource("malformedBodies")
    void malformedBodiesAreBadRequests(String body) throws Exception {
        mvc.perform(patch("/competitions/7/status").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
        verify(service, never()).changeStatus(any(), any());
    }

    static Stream<String> malformedBodies() {
        return Stream.of("{\"status\":", "");
    }

    @Test
    void aNonNumericCompetitionIdIsABadRequest() throws Exception {
        mvc.perform(patch("/competitions/abc/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EN_CURSO\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
        verify(service, never()).changeStatus(any(), any());
    }

    @ParameterizedTest
    @MethodSource("serviceFailures")
    void mapsStatusChangeFailuresToStructuredErrors(RuntimeException failure, int code) throws Exception {
        when(service.changeStatus(eq(7), any())).thenThrow(failure);

        mvc.perform(patch("/competitions/7/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EN_CURSO\"}"))
                .andExpect(status().is(code)).andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.errors").exists());
    }

    // ---- GET /competitions?teamId=

    @Test
    void listsTheTeamsCompetitionsWithoutTheAccessKey() throws Exception {
        when(service.listByTeam(1)).thenReturn(new CompetitionListResponse(1, 1, List.of(new CompetitionListItem(7,
                "Simulacro", null, CompetitionAccessType.PRIVADO_PASS, PenaltyRule.ICPC_20_MIN, 300, 60,
                CompetitionStatus.EN_CURSO, START, START.plusHours(5), 3L))));

        mvc.perform(get("/competitions").param("teamId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamId").value(1))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(7))
                .andExpect(jsonPath("$.items[0].eventName").value("Simulacro"))
                .andExpect(jsonPath("$.items[0].status").value("EN_CURSO"))
                .andExpect(jsonPath("$.items[0].accessType").value("PRIVADO_PASS"))
                .andExpect(jsonPath("$.items[0].problemsCount").value(3))
                .andExpect(jsonPath("$.items[0].accessKey").doesNotExist());
        verify(service).listByTeam(1);
    }

    @Test
    void aMissingTeamIdIsLeftToTheServiceToReject() throws Exception {
        when(service.listByTeam(null)).thenThrow(
                new CompetitionValidationException(Map.of("teamId", "El equipo es obligatorio")));

        mvc.perform(get("/competitions")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.teamId").exists());
        verify(service).listByTeam(null);
    }

    @Test
    void aNonNumericTeamIdIsABadRequestThatNeverReachesTheService() throws Exception {
        mvc.perform(get("/competitions").param("teamId", "abc")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
        verify(service, never()).listByTeam(any());
    }

    @ParameterizedTest
    @MethodSource("serviceFailures")
    void mapsListFailuresToStructuredErrors(RuntimeException failure, int code) throws Exception {
        when(service.listByTeam(1)).thenThrow(failure);

        mvc.perform(get("/competitions").param("teamId", "1"))
                .andExpect(status().is(code)).andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.errors").exists());
    }

    static Stream<Arguments> serviceFailures() {
        return Stream.of(
                Arguments.of(new CompetitionValidationException(Map.of("teamId", "Obligatorio")), 400),
                Arguments.of(new UnauthorizedException("Sin sesión"), 401),
                Arguments.of(new ForbiddenException("No autorizado"), 403),
                Arguments.of(new NotFoundException("La competencia no existe"), 404),
                Arguments.of(new ConflictException("Transición no permitida"), 409),
                Arguments.of(new DataAccessResourceFailureException("Internal SQL"), 503),
                Arguments.of(new IllegalStateException("boom"), 500));
    }
}

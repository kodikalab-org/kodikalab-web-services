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
import com.kodika.kodikalab.competitions.competition.dto.CompetitionResponse;
import com.kodika.kodikalab.competitions.competition.dto.CreateCompetitionRequest;
import com.kodika.kodikalab.competitions.competitionproblem.CompetitionProblemService;
import com.kodika.kodikalab.competitions.officialresult.OfficialResultService;
import com.kodika.kodikalab.competitions.problemresolution.ProblemResolutionService;
import java.time.OffsetDateTime;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CompetitionCreationControllerTests {
    static final OffsetDateTime START = OffsetDateTime.parse("2026-10-20T14:00:00-05:00");
    static final String VALID = "{\"teamId\":1,\"eventName\":\"Simulacro 1\","
            + "\"startsAt\":\"2026-10-20T14:00:00-05:00\",\"endsAt\":\"2026-10-20T19:00:00-05:00\"}";

    CompetitionService service;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(CompetitionService.class);
        var controller = new CompetitionController(service, mock(CompetitionProblemService.class),
                mock(ProblemResolutionService.class), mock(CategoryService.class), mock(OfficialResultService.class));
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new CompetitionExceptionHandler()).build();
    }

    @Test
    void createsAndNeverReturnsTheAccessKey() throws Exception {
        var request = new CreateCompetitionRequest(1, "Privada", "desc", CompetitionAccessType.PRIVADO_PASS, "secreto",
                PenaltyRule.IOI_POINTS, 15, CompetitionStatus.FINALIZADA, START, START.plusHours(5));
        when(service.create(request)).thenReturn(new CompetitionResponse(7, 1, "Privada", "desc",
                CompetitionAccessType.PRIVADO_PASS, PenaltyRule.IOI_POINTS, 300, 15, CompetitionStatus.FINALIZADA, START,
                START.plusHours(5)));

        mvc.perform(post("/competitions").contentType(MediaType.APPLICATION_JSON).content("""
                        {"teamId":1,"eventName":"Privada","description":"desc","accessType":"PRIVADO_PASS",
                         "accessKey":"secreto","penaltyRule":"IOI_POINTS","scoreboardFreezeMinutes":15,
                         "status":"FINALIZADA","startsAt":"2026-10-20T14:00:00-05:00",
                         "endsAt":"2026-10-20T19:00:00-05:00","coachId":999}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.teamId").value(1))
                .andExpect(jsonPath("$.accessType").value("PRIVADO_PASS"))
                .andExpect(jsonPath("$.durationMinutes").value(300))
                .andExpect(jsonPath("$.status").value("FINALIZADA"))
                .andExpect(jsonPath("$.accessKey").doesNotExist());
        verify(service).create(request);
    }

    @Test
    void omittedOptionalFieldsArriveAsNullSoTheServiceAppliesDefaults() throws Exception {
        when(service.create(any())).thenReturn(new CompetitionResponse(7, 1, "Simulacro 1", null,
                CompetitionAccessType.PUBLICO_GRUPO, PenaltyRule.ICPC_20_MIN, 300, 60, CompetitionStatus.PROGRAMADA,
                START, START.plusHours(5)));

        mvc.perform(post("/competitions").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated());
        verify(service).create(new CreateCompetitionRequest(1, "Simulacro 1", null, null, null, null, null, null, START,
                START.plusHours(5)));
    }

    @Test
    void reportsEveryFieldWithAWrongTypeAndNeverCallsTheService() throws Exception {
        mvc.perform(post("/competitions").contentType(MediaType.APPLICATION_JSON).content("""
                        {"teamId":"1","eventName":5,"description":true,"accessType":"OTRO","accessKey":12,
                         "penaltyRule":"x","scoreboardFreezeMinutes":1.5,"status":"CERRADA",
                         "startsAt":"ayer","endsAt":123}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.errors.teamId").exists())
                .andExpect(jsonPath("$.errors.eventName").exists())
                .andExpect(jsonPath("$.errors.description").exists())
                .andExpect(jsonPath("$.errors.accessType").exists())
                .andExpect(jsonPath("$.errors.accessKey").exists())
                .andExpect(jsonPath("$.errors.penaltyRule").exists())
                .andExpect(jsonPath("$.errors.scoreboardFreezeMinutes").exists())
                .andExpect(jsonPath("$.errors.status").exists())
                .andExpect(jsonPath("$.errors.startsAt").exists())
                .andExpect(jsonPath("$.errors.endsAt").exists());
        verify(service, never()).create(any());
    }

    @ParameterizedTest
    @MethodSource("malformedBodies")
    void malformedBodiesAreBadRequests(String body) throws Exception {
        mvc.perform(post("/competitions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
        verify(service, never()).create(any());
    }

    static Stream<String> malformedBodies() {
        return Stream.of("{\"teamId\":", "[]", "\"texto\"", "");
    }

    @ParameterizedTest
    @MethodSource("serviceFailures")
    void mapsServiceFailuresToStructuredErrors(RuntimeException failure, int code) throws Exception {
        when(service.create(any())).thenThrow(failure);

        mvc.perform(post("/competitions").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().is(code)).andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.errors").exists());
    }

    static Stream<Arguments> serviceFailures() {
        return Stream.of(
                Arguments.of(new CompetitionValidationException(Map.of("eventName", "Obligatorio")), 400),
                Arguments.of(new UnauthorizedException("Sin sesión"), 401),
                Arguments.of(new ForbiddenException("No autorizado"), 403),
                Arguments.of(new NotFoundException("El equipo no existe"), 404),
                Arguments.of(new ConflictException("Duplicada"), 409),
                Arguments.of(new DataAccessResourceFailureException("Internal SQL"), 503),
                Arguments.of(new IllegalStateException("boom"), 500));
    }
}

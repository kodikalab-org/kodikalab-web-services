package com.kodika.kodikalab.analytics;

import com.kodika.kodikalab.analytics.dto.TeamProgressResponse;
import com.kodika.kodikalab.analytics.dto.TeamProgressResponse.Registration;
import com.kodika.kodikalab.common.exception.BadRequestException;
import com.kodika.kodikalab.common.exception.ConflictException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.common.exception.UnauthorizedException;
import com.kodika.kodikalab.competitions.problemresolution.ResolutionValidationException;
import com.kodika.kodikalab.competitions.problemresolution.Verdict;
import com.kodika.kodikalab.competitions.problemresolution.dto.ManualResolutionRequest;
import com.kodika.kodikalab.competitions.problemresolution.dto.TeamResolutionData;
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

class IndependentProgressControllerTests {
    IndependentProgressService service;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(IndependentProgressService.class);
        mvc = MockMvcBuilders.standaloneSetup(new IndependentProgressController(service))
                .setControllerAdvice(new IndependentProgressExceptionHandler()).build();
    }

    @Test
    void registrationReturnsOnlySelectedTeamsPersonalProgressAndManualMarker() throws Exception {
        var request = new ManualResolutionRequest("Java 21", "https://example.com/submission/1");
        var resolution = new TeamResolutionData(60, 20, 1, 50, 30, 1, 40, Verdict.ACCEPTED);
        when(service.register(1, 50, request)).thenReturn(new Registration(resolution, "MANUAL_PROVISIONAL",
                new TeamProgressResponse(1, 20, 10, 1)));
        mvc.perform(post("/api/competitions/teams/1/problems/50/resolutions").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"language":"Java 21","evidenceUrl":"https://example.com/submission/1",
                                 "userId":999,"membershipId":999,"teamId":999,"verdict":"WRONG_ANSWER"}
                                """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.registrationMethod").value("MANUAL_PROVISIONAL"))
                .andExpect(jsonPath("$.resolution.membershipId").value(20))
                .andExpect(jsonPath("$.resolution.verdict").value("ACCEPTED"))
                .andExpect(jsonPath("$.progress.teamId").value(1))
                .andExpect(jsonPath("$.progress.userId").value(10))
                .andExpect(jsonPath("$.progress.acceptedProblems").value(1));
        verify(service).register(1, 50, request);
    }

    @Test
    void personalQueryReturnsTeamContextAndDoesNotUseUserParameter() throws Exception {
        when(service.getProgress(2)).thenReturn(new TeamProgressResponse(2, 21, 10, 0));
        mvc.perform(get("/analytics/teams/2/progress/me").param("userId", "999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.teamId").value(2))
                .andExpect(jsonPath("$.membershipId").value(21)).andExpect(jsonPath("$.userId").value(10))
                .andExpect(jsonPath("$.acceptedProblems").value(0));
        verify(service).getProgress(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"text", "2147483648"})
    void malformedContextOrAssignmentDoesNotCallService(String id) throws Exception {
        mvc.perform(get("/analytics/teams/" + id + "/progress/me")).andExpect(status().isBadRequest());
        mvc.perform(post("/competitions/teams/1/problems/" + id + "/resolutions")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"language\":\"Java 21\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    static Stream<Arguments> invalidTypes() {
        return Stream.of(Arguments.of("[]", "body"),
                Arguments.of("{\"language\":21}", "language"),
                Arguments.of("{\"language\":true}", "language"),
                Arguments.of("{\"evidenceUrl\":{}}", "evidenceUrl"));
    }

    @ParameterizedTest
    @MethodSource("invalidTypes")
    void invalidJsonTypesIdentifyFieldsWithoutCoercion(String body, String field) throws Exception {
        mvc.perform(post("/competitions/teams/1/problems/50/resolutions")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors." + field).exists());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{"})
    void missingOrMalformedJsonDoesNotCallService(String body) throws Exception {
        mvc.perform(post("/competitions/teams/1/problems/50/resolutions")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void invalidFieldsAreReportedWithoutPublishingProgress() throws Exception {
        when(service.register(1, 50, new ManualResolutionRequest(null, null)))
                .thenThrow(new ResolutionValidationException(Map.of("language", "El lenguaje es obligatorio")));
        mvc.perform(post("/competitions/teams/1/problems/50/resolutions")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.language").exists())
                .andExpect(jsonPath("$.progress").doesNotExist());
    }

    @Test
    void inconsistentMetricsReturn409AndDoNotPublishRegistration() throws Exception {
        when(service.register(1, 50, new ManualResolutionRequest("Java 21", null)))
                .thenThrow(new RankingDataException("Datos inconsistentes", Map.of("resolutions", "Equipos cruzados")));
        mvc.perform(post("/competitions/teams/1/problems/50/resolutions")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"language\":\"Java 21\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errors.resolutions").exists())
                .andExpect(jsonPath("$.resolution").doesNotExist()).andExpect(jsonPath("$.progress").doesNotExist());
    }

    static Stream<Arguments> failures() {
        return Stream.of(Arguments.of(new UnauthorizedException("Perfil"), 401),
                Arguments.of(new ForbiddenException("Seleccione un contexto de equipo válido"), 403),
                Arguments.of(new BadRequestException("Equipo inválido"), 400),
                Arguments.of(new NotFoundException("Equipo inexistente"), 404),
                Arguments.of(new ConflictException("Resolución duplicada"), 409),
                Arguments.of(new DataAccessResourceFailureException("SQL privado"), 503),
                Arguments.of(new CannotCreateTransactionException("Credenciales privadas"), 503),
                Arguments.of(new IllegalStateException("Detalle privado"), 500));
    }

    @ParameterizedTest
    @MethodSource("failures")
    void errorsDoNotExposeInternalInformationOrPersonalProgress(RuntimeException failure, int code) throws Exception {
        when(service.getProgress(1)).thenThrow(failure);
        mvc.perform(get("/analytics/teams/1/progress/me")).andExpect(status().is(code))
                .andExpect(jsonPath("$.message").exists()).andExpect(jsonPath("$.errors").isMap())
                .andExpect(jsonPath("$.acceptedProblems").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("privad"))));
    }
}

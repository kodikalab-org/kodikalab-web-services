package com.kodika.kodikalab.analytics;

import com.kodika.kodikalab.analytics.dto.TopicProgressResponse;
import com.kodika.kodikalab.analytics.dto.TopicProgressResponse.TopicProgress;
import com.kodika.kodikalab.analytics.dto.TopicProgressResponse.TopicStatus;
import com.kodika.kodikalab.common.exception.BadRequestException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.common.exception.UnauthorizedException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TopicProgressControllerTests {
    TopicProgressService service;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(TopicProgressService.class);
        mvc = MockMvcBuilders.standaloneSetup(new TopicProgressController(service))
                .setControllerAdvice(new TopicProgressExceptionHandler()).build();
    }

    @Test
    void returnsTheProgressByTopicWithTheContractFields() throws Exception {
        when(service.getProgress(1)).thenReturn(new TopicProgressResponse(1, 7, 10, 3, 1, 0, "criterio", List.of(
                new TopicProgress(20, "Programación dinámica", 1, 0, 1, 0, new BigDecimal("0.00"),
                        TopicStatus.SIN_ACTIVIDAD, true),
                new TopicProgress(10, "Grafos", 2, 1, 1, 0, new BigDecimal("50.00"), TopicStatus.EN_PROGRESO, false))));

        mvc.perform(get("/analytics/teams/1/progress/me/topics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamId").value(1))
                .andExpect(jsonPath("$.membershipId").value(7))
                .andExpect(jsonPath("$.userId").value(10))
                .andExpect(jsonPath("$.assignedProblems").value(3))
                .andExpect(jsonPath("$.solvedProblems").value(1))
                .andExpect(jsonPath("$.unclassifiedProblems").value(0))
                .andExpect(jsonPath("$.reinforcementCriterion").value("criterio"))
                .andExpect(jsonPath("$.topics.length()").value(2))
                .andExpect(jsonPath("$.topics[0].topicName").value("Programación dinámica"))
                .andExpect(jsonPath("$.topics[0].status").value("SIN_ACTIVIDAD"))
                .andExpect(jsonPath("$.topics[0].needsReinforcement").value(true))
                .andExpect(jsonPath("$.topics[0].coveragePercentage").value(0.0))
                .andExpect(jsonPath("$.topics[1].status").value("EN_PROGRESO"))
                .andExpect(jsonPath("$.topics[1].coveragePercentage").value(50.0))
                .andExpect(jsonPath("$.topics[1].needsReinforcement").value(false));
        verify(service).getProgress(1);
    }

    @Test
    void aNonNumericTeamIdIsABadRequestThatNeverReachesTheService() throws Exception {
        mvc.perform(get("/analytics/teams/abc/progress/me/topics"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
        verify(service, never()).getProgress(any());
    }

    @ParameterizedTest
    @MethodSource("serviceFailures")
    void mapsServiceFailuresToStructuredErrors(RuntimeException failure, int code) throws Exception {
        when(service.getProgress(1)).thenThrow(failure);

        mvc.perform(get("/analytics/teams/1/progress/me/topics"))
                .andExpect(status().is(code)).andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    void inconsistentDataIsAConflictThatNamesTheAffectedInformation() throws Exception {
        when(service.getProgress(1)).thenThrow(new TopicProgressDataException("No se pudo calcular un progreso por tema válido",
                Map.of("attempts[0].competitionProblemId", "El intento no corresponde a un problema asignado")));

        mvc.perform(get("/analytics/teams/1/progress/me/topics"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("No se pudo calcular un progreso por tema válido"))
                .andExpect(jsonPath("$.errors['attempts[0].competitionProblemId']").exists());
    }

    @Test
    void anUnavailableDatabaseInvitesToRetryWithoutLeakingDetails() throws Exception {
        when(service.getProgress(1)).thenThrow(new DataAccessResourceFailureException("SQL privado"));

        mvc.perform(get("/analytics/teams/1/progress/me/topics"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("vuelva a intentarlo")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SQL"))));
    }

    static Stream<Arguments> serviceFailures() {
        return Stream.of(
                Arguments.of(new BadRequestException("id"), 400),
                Arguments.of(new UnauthorizedException("Sin sesión"), 401),
                Arguments.of(new ForbiddenException("No autorizado"), 403),
                Arguments.of(new NotFoundException("El equipo no existe"), 404),
                Arguments.of(new TopicProgressDataException("Datos", Map.of("topics", "Falta")), 409),
                Arguments.of(new DataAccessResourceFailureException("Internal SQL"), 503),
                Arguments.of(new IllegalStateException("boom"), 500));
    }
}

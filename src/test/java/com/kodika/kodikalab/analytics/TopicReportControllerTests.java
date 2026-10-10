package com.kodika.kodikalab.analytics;

import com.kodika.kodikalab.analytics.dto.TeamTopicReportResponse;
import com.kodika.kodikalab.analytics.dto.TeamTopicReportResponse.TopicPerformance;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.CannotCreateTransactionException;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TopicReportControllerTests {
    TeamTopicReportService service;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(TeamTopicReportService.class);
        mvc = MockMvcBuilders.standaloneSetup(new TopicReportController(service))
                .setControllerAdvice(new TopicReportExceptionHandler()).build();
    }

    @Test
    void returnsStructuredReportAndDoesNotUseSuppliedUserId() throws Exception {
        when(service.getReport(1)).thenReturn(new TeamTopicReportResponse(1,
                "DISTINCT_SOLVED_PROBLEMS_OVER_ASSIGNED_PROBLEMS", "EXACT_PROPORTION_MINIMUM_ALL_TIES",
                "Se comparan proporciones exactas", 2, 1, List.of(
                new TopicPerformance(10, "Grafos", 3, 1, 2, 1, 1, new BigDecimal("33.33"), true),
                new TopicPerformance(20, "Árboles", 6, 2, 4, 2, 0, new BigDecimal("33.33"), true))));

        mvc.perform(get("/api/analytics/teams/1/weaknesses").contextPath("/api").param("userId", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamId").value(1))
                .andExpect(jsonPath("$.pendingResolutions").value(1))
                .andExpect(jsonPath("$.topics.length()").value(2))
                .andExpect(jsonPath("$.topics[0].lowestCoverage").value(true))
                .andExpect(jsonPath("$.topics[1].lowestCoverage").value(true))
                .andExpect(jsonPath("$.topics[1].unsolvedProblems").value(4))
                .andExpect(jsonPath("$.comparisonExplanation").value("Se comparan proporciones exactas"));
        verify(service).getReport(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"text", "2147483648"})
    void malformedIdReturns400WithoutCallingService(String id) throws Exception {
        mvc.perform(get("/analytics/teams/" + id + "/weaknesses"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    static Stream<Arguments> failures() {
        return Stream.of(
                Arguments.of(new UnauthorizedException("Perfil"), 401, "Debe iniciar sesión para consultar el reporte de temas"),
                Arguments.of(new ForbiddenException("Acceso denegado"), 403, "Acceso denegado"),
                Arguments.of(new NotFoundException("El equipo no existe"), 404, "El equipo no existe"),
                Arguments.of(new BadRequestException("ID"), 400, "El identificador del equipo debe ser un entero positivo"),
                Arguments.of(new DataAccessResourceFailureException("SQL interno"), 503,
                        "La información necesaria para el reporte no está disponible"),
                Arguments.of(new CannotCreateTransactionException("Credenciales internas"), 503,
                        "La información necesaria para el reporte no está disponible"),
                Arguments.of(new IllegalStateException("Detalle interno"), 500, "No se pudo generar el reporte de temas"));
    }

    @ParameterizedTest
    @MethodSource("failures")
    void mapsErrorsWithoutPartialConclusions(RuntimeException failure, int statusCode, String message) throws Exception {
        when(service.getReport(1)).thenThrow(failure);
        mvc.perform(get("/analytics/teams/1/weaknesses"))
                .andExpect(status().is(statusCode))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.errors").isMap())
                .andExpect(jsonPath("$.topics").doesNotExist());
    }

    @Test
    void insufficientOrInconsistentDataIdentifiesAffectedInformation() throws Exception {
        when(service.getReport(1)).thenThrow(new TopicReportDataException("Información insuficiente",
                Map.of("topics", "Falta la clasificación de los problemas: [100]")));
        mvc.perform(get("/analytics/teams/1/weaknesses"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.topics").value("Falta la clasificación de los problemas: [100]"))
                .andExpect(jsonPath("$.topics").doesNotExist());
    }
}

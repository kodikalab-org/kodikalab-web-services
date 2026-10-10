package com.kodika.kodikalab.analytics;

import com.kodika.kodikalab.analytics.dto.TeamRankingResponse;
import com.kodika.kodikalab.analytics.dto.LastValidRanking;
import java.time.OffsetDateTime;
import com.kodika.kodikalab.analytics.dto.TeamRankingResponse.MemberStanding;
import com.kodika.kodikalab.analytics.dto.TeamRankingResponse.Status;
import com.kodika.kodikalab.common.exception.BadRequestException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.common.exception.UnauthorizedException;
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

class AnalyticsControllerTests {
    StoredTeamRankingService service;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(StoredTeamRankingService.class);
        mvc = MockMvcBuilders.standaloneSetup(new AnalyticsController(service))
                .setControllerAdvice(new AnalyticsExceptionHandler()).build();
    }

    @Test
    void returnsStructuredRankingUsingConfiguredApiContext() throws Exception {
        when(service.getRanking(1)).thenReturn(new TeamRankingResponse(1, Status.CALCULATED,
                "DISTINCT_ACCEPTED_PROBLEMS_DESC", "SHARED_POSITION_1_1_3", List.of(
                new MemberStanding(1, 10, "Usuario Prueba", 2, 1))));

        mvc.perform(get("/api/analytics/teams/1/standings").contextPath("/api").param("userId", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamId").value(1))
                .andExpect(jsonPath("$.status").value("CALCULATED"))
                .andExpect(jsonPath("$.orderingCriterion").value("DISTINCT_ACCEPTED_PROBLEMS_DESC"))
                .andExpect(jsonPath("$.tieCriterion").value("SHARED_POSITION_1_1_3"))
                .andExpect(jsonPath("$.members[0].acceptedProblems").value(2))
                .andExpect(jsonPath("$.members[0].position").value(1))
                .andExpect(jsonPath("$.members[0].passwordHash").doesNotExist());
        verify(service).getRanking(1);
    }

    @Test
    void teamWithoutActivityHasExplicitStatusAndNoPositions() throws Exception {
        when(service.getRanking(1)).thenReturn(new TeamRankingResponse(1, Status.NO_ACTIVITY,
                "DISTINCT_ACCEPTED_PROBLEMS_DESC", "SHARED_POSITION_1_1_3", List.of()));

        mvc.perform(get("/analytics/teams/1/standings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_ACTIVITY"))
                .andExpect(jsonPath("$.members").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"text", "2147483648"})
    void malformedTeamIdIsBadRequest(String id) throws Exception {
        mvc.perform(get("/analytics/teams/" + id + "/standings"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isMap());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @MethodSource("errors")
    void mapsFailuresWithoutLeakingInternalDetails(RuntimeException exception, int statusCode,
                                                   String message) throws Exception {
        when(service.getRanking(1)).thenThrow(exception);

        mvc.perform(get("/analytics/teams/1/standings"))
                .andExpect(status().is(statusCode))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.errors").isMap())
                .andExpect(jsonPath("$.members").doesNotExist());
    }

    static Stream<Arguments> errors() {
        return Stream.of(
                Arguments.of(new UnauthorizedException("Perfil"), 401, "Debe iniciar sesión para consultar el ranking"),
                Arguments.of(new ForbiddenException("Acceso denegado"), 403, "Acceso denegado"),
                Arguments.of(new NotFoundException("El equipo no existe"), 404, "El equipo no existe"),
                Arguments.of(new BadRequestException("ID inválido"), 400,
                        "El identificador del equipo debe ser un entero positivo"),
                Arguments.of(new DataAccessResourceFailureException("SQL interno"), 503,
                        "La información necesaria para el ranking no está disponible"),
                Arguments.of(new CannotCreateTransactionException("Credenciales internas"), 503,
                        "La información necesaria para el ranking no está disponible"),
                Arguments.of(new IllegalStateException("Detalle interno"), 500, "No se pudo calcular el ranking"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void previousRankingIsExplicitlySeparateFromFailedCurrentCalculation(boolean inconsistent) throws Exception {
        var ranking = new TeamRankingResponse(1, Status.CALCULATED, "DISTINCT_ACCEPTED_PROBLEMS_DESC",
                "SHARED_POSITION_1_1_3", List.of(new MemberStanding(1, 10, "Usuario Prueba", 2, 1)));
        RuntimeException failure = inconsistent
                ? new RankingDataException("Datos inconsistentes", Map.of("resolutions[0].teamId", "Equipo incorrecto"))
                : new DataAccessResourceFailureException("Internal SQL");
        when(service.getRanking(1)).thenThrow(new RankingRecoveryException(failure,
                new LastValidRanking(OffsetDateTime.parse("2026-10-09T12:00:00Z"), ranking)));

        var response = mvc.perform(get("/analytics/teams/1/standings"))
                .andExpect(status().is(inconsistent ? 409 : 503))
                .andExpect(jsonPath("$.lastValidRanking.calculatedAt").exists())
                .andExpect(jsonPath("$.lastValidRanking.ranking.teamId").value(1))
                .andExpect(jsonPath("$.lastValidRanking.ranking.members[0].acceptedProblems").value(2))
                .andExpect(jsonPath("$.members").doesNotExist());
        if (inconsistent) {
            response.andExpect(jsonPath("$.errors['resolutions[0].teamId']").value("Equipo incorrecto"));
        } else {
            response.andExpect(jsonPath("$.message")
                    .value("La información necesaria para el ranking no está disponible"));
        }
    }

    @Test
    void inconsistentDataIdentifiesAffectedInformationWithoutPartialRanking() throws Exception {
        when(service.getRanking(1)).thenThrow(new RankingDataException("No se pudo calcular un ranking válido",
                Map.of("resolutions[1].teamId", "La membresía y la competencia deben pertenecer al equipo consultado")));

        mvc.perform(get("/analytics/teams/1/standings"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors['resolutions[1].teamId']")
                        .value("La membresía y la competencia deben pertenecer al equipo consultado"))
                .andExpect(jsonPath("$.members").doesNotExist());
    }
}

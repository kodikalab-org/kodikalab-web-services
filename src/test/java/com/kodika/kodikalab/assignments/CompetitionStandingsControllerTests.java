package com.kodika.kodikalab.assignments;

import com.kodika.kodikalab.assignments.dto.CompetitionStandingsResponse;
import com.kodika.kodikalab.assignments.dto.CompetitionStandingsResponse.Cell;
import com.kodika.kodikalab.assignments.dto.CompetitionStandingsResponse.ProblemInfo;
import com.kodika.kodikalab.assignments.dto.CompetitionStandingsResponse.Row;
import com.kodika.kodikalab.common.exception.FieldValidationException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.competitions.competition.CompetitionStatus;
import com.kodika.kodikalab.competitions.competition.PenaltyRule;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CompetitionStandingsControllerTests {
    static final OffsetDateTime START = OffsetDateTime.parse("2026-10-20T14:00:00Z");

    CompetitionStandingsService standings;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        standings = mock(CompetitionStandingsService.class);
        mvc = MockMvcBuilders.standaloneSetup(new CompetitionStandingsController(standings))
                .setControllerAdvice(new AssignmentExceptionHandler()).build();
    }

    @Test
    void returnsTheScoreboardWithTheDocumentedShape() throws Exception {
        when(standings.standings(5)).thenReturn(new CompetitionStandingsResponse(5, 3, "Semanal 12",
                CompetitionStatus.EN_CURSO, PenaltyRule.ICPC_20_MIN, START, START.plusHours(5),
                List.of(new ProblemInfo(11, "A", "Watermelon", 100)),
                List.of(new Row(1, 7, "Ana Pérez", 1, 100, 12, List.of(
                        new Cell(11, "A", AssignmentStatus.RESUELTO, 1, 12))),
                        new Row(2, 8, "Beto Ríos", 0, 0, 0, List.of(
                                new Cell(11, "A", AssignmentStatus.SIN_INTENTOS, 0, null))))));

        mvc.perform(get("/competitions/5/standings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.competitionId").value(5))
                .andExpect(jsonPath("$.teamId").value(3))
                .andExpect(jsonPath("$.eventName").value("Semanal 12"))
                .andExpect(jsonPath("$.status").value("EN_CURSO"))
                .andExpect(jsonPath("$.penaltyRule").value("ICPC_20_MIN"))
                .andExpect(jsonPath("$.startsAt").exists())
                .andExpect(jsonPath("$.endsAt").exists())
                .andExpect(jsonPath("$.problems[0].competitionProblemId").value(11))
                .andExpect(jsonPath("$.problems[0].letter").value("A"))
                .andExpect(jsonPath("$.problems[0].title").value("Watermelon"))
                .andExpect(jsonPath("$.problems[0].score").value(100))
                .andExpect(jsonPath("$.rows.length()").value(2))
                .andExpect(jsonPath("$.rows[0].position").value(1))
                .andExpect(jsonPath("$.rows[0].userId").value(7))
                .andExpect(jsonPath("$.rows[0].fullName").value("Ana Pérez"))
                .andExpect(jsonPath("$.rows[0].solved").value(1))
                .andExpect(jsonPath("$.rows[0].score").value(100))
                .andExpect(jsonPath("$.rows[0].penaltyMinutes").value(12))
                .andExpect(jsonPath("$.rows[0].cells[0].competitionProblemId").value(11))
                .andExpect(jsonPath("$.rows[0].cells[0].letter").value("A"))
                .andExpect(jsonPath("$.rows[0].cells[0].result").value("RESUELTO"))
                .andExpect(jsonPath("$.rows[0].cells[0].attempts").value(1))
                .andExpect(jsonPath("$.rows[0].cells[0].minutes").value(12))
                .andExpect(jsonPath("$.rows[1].cells[0].result").value("SIN_INTENTOS"))
                .andExpect(jsonPath("$.rows[1].cells[0].minutes").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void mapsForbiddenToHttp403() throws Exception {
        when(standings.standings(5)).thenThrow(new ForbiddenException("No pertenece a este equipo"));

        mvc.perform(get("/competitions/5/standings"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("No pertenece a este equipo"));
    }

    @Test
    void mapsAnUnknownCompetitionToHttp404() throws Exception {
        when(standings.standings(99)).thenThrow(new NotFoundException("La competencia no existe"));

        mvc.perform(get("/competitions/99/standings"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("La competencia no existe"));
    }

    @Test
    void mapsAnInvalidIdToHttp400() throws Exception {
        when(standings.standings(0)).thenThrow(new FieldValidationException("Los criterios de consulta deben corregirse",
                Map.of("competitionId", "La competencia debe ser un entero positivo")));

        mvc.perform(get("/competitions/0/standings"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.competitionId").exists());
    }

    @Test
    void rejectsANonNumericIdWithHttp400() throws Exception {
        mvc.perform(get("/competitions/abc/standings")).andExpect(status().isBadRequest());
    }
}

package com.kodika.kodikalab.agent.app.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kodika.kodikalab.agent.app.dto.AgentResponse;
import com.kodika.kodikalab.agent.app.dto.ResponseData;
import com.kodika.kodikalab.analytics.TeamTopicReportService;
import com.kodika.kodikalab.analytics.TopicReportDataException;
import com.kodika.kodikalab.analytics.dto.TeamTopicReportResponse;
import com.kodika.kodikalab.analytics.dto.TeamTopicReportResponse.TopicPerformance;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.teams.studygroup.StudyGroupService;
import com.kodika.kodikalab.teams.studygroup.dto.MyTeamResponse;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserStatus;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

/** Exercises the tool through the same callback interface the model uses. */
class InsightToolsTests {
    private final CurrentUserResolver currentUser = mock(CurrentUserResolver.class);
    private final StudyGroupService groups = mock(StudyGroupService.class);
    private final TeamTopicReportService reports = mock(TeamTopicReportService.class);
    private final ToolCallback weakTopics = ToolCallbacks.from(new InsightTools(currentUser, groups, reports))[0];
    private final ResponseData data = new ResponseData();
    private final User user = mock(User.class);

    @BeforeEach
    void coachWithTwoTeams() {
        when(user.getRole()).thenReturn(Role.COACH);
        when(user.getStatus()).thenReturn(UserStatus.ACTIVO);
        when(currentUser.currentUser()).thenReturn(user);
        var teams = List.of(team(7, "Algoritmos Básicos"), team(8, "Grafos Avanzados"));
        when(groups.findMyTeams()).thenReturn(teams);
        when(reports.getReport(any())).thenReturn(report(topic(1, "Grafos", 4, 1, "25.00", true),
                topic(2, "Cadenas", 2, 2, "100.00", false)));
    }

    @Test
    void theModelOnlySeesTheTeamNameParameter() {
        assertThat(weakTopics.getToolDefinition().name()).isEqualTo("getWeakTopics");
        assertThat(weakTopics.getToolDefinition().inputSchema()).contains("teamName")
                .doesNotContain("teamId").doesNotContain("userId").doesNotContain("context");
    }

    @Test
    void usesTheOpenTeamAndLeavesTheFiguresToTheApplication() {
        String result = call("{}", 7);

        verify(reports).getReport(7);
        assertThat(result).contains("\"status\":\"OK\"").contains("Grafos").doesNotContain("Cadenas")
                .doesNotContain("25.00");
        AgentResponse response = data.toResponse("c-1", "insights", "text");
        assertThat(response.chart().labels()).containsExactly("Grafos", "Cadenas");
        assertThat(response.chart().values()).containsExactly(new BigDecimal("25.00"), new BigDecimal("100.00"));
    }

    @Test
    void aNamedTeamWinsOverTheOpenOneIgnoringCaseAndAccents() {
        call("{\"teamName\":\"algoritmos basicos\"}", 8);

        verify(reports).getReport(7);
    }

    @Test
    void asksForTheTeamWhenNoneIsOpenOrNamed() {
        String result = call("{}", null);

        assertThat(result).contains("NEEDS_TEAM").contains("Algoritmos Básicos").contains("Grafos Avanzados");
        verify(reports, never()).getReport(any());
        assertThat(data.toResponse("c-1", "insights", "text").chart()).isNull();
    }

    @Test
    void asksAgainWhenTheNameMatchesNoOwnTeamEvenIfAnotherTeamIsOpen() {
        String result = call("{\"teamName\":\"Equipo ajeno\"}", 7);

        assertThat(result).contains("NEEDS_TEAM");
        verify(reports, never()).getReport(any());
    }

    @Test
    void asksAgainWhenThePartialNameIsAmbiguous() {
        String result = call("{\"teamName\":\"os\"}", null);

        assertThat(result).contains("NEEDS_TEAM");
        verify(reports, never()).getReport(any());
    }

    @Test
    void aPractitionerIsDeniedBeforeAnyTeamIsRead() {
        when(user.getRole()).thenReturn(Role.PRACTICANTE);

        String result = call("{\"teamName\":\"Grafos Avanzados\"}", 7);

        assertThat(result).contains("DENIED");
        verify(groups, never()).findMyTeams();
        verify(reports, never()).getReport(any());
    }

    @Test
    void anOpenTeamOfAnotherCoachIsDeniedByTheService() {
        when(reports.getReport(99)).thenThrow(new ForbiddenException("No tiene autorización"));

        String result = call("{}", 99);

        assertThat(result).contains("DENIED").contains("No tiene autorización");
        assertThat(data.toResponse("c-1", "insights", "text").chart()).isNull();
    }

    @Test
    void insufficientDataIsReportedWithoutInventingTopics() {
        when(reports.getReport(7)).thenThrow(new TopicReportDataException("No se pudo generar un reporte",
                Map.of("members", "No hay integrantes activos")));

        String result = call("{}", 7);

        assertThat(result).contains("INSUFFICIENT_DATA").contains("No hay integrantes activos");
    }

    private String call(String input, Integer openTeamId) {
        Map<String, Object> context = new HashMap<>();
        context.put(ResponseData.CONTEXT_KEY, data);
        if (openTeamId != null) {
            context.put(ResponseData.TEAM_ID_KEY, openTeamId);
        }
        return weakTopics.call(input, new ToolContext(context));
    }

    private static MyTeamResponse team(int id, String name) {
        return new MyTeamResponse(id, name, null, null, null, null, null, null, null, null, null, null);
    }

    private static TopicPerformance topic(int id, String name, int assigned, int solved, String coverage,
                                          boolean lowest) {
        return new TopicPerformance(id, name, assigned, solved, assigned - solved, 1, 0, new BigDecimal(coverage),
                lowest);
    }

    private static TeamTopicReportResponse report(TopicPerformance... topics) {
        return new TeamTopicReportResponse(7, "metric", "criterion", "explanation", 3, 0, List.of(topics));
    }
}

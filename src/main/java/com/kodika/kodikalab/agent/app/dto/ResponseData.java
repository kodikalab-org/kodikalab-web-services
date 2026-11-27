package com.kodika.kodikalab.agent.app.dto;

import java.util.List;
import org.springframework.ai.chat.model.ToolContext;

/**
 * Datos que las tools reúnen durante un mensaje. Se crea uno por petición y viaja en el {@link ToolContext}, que el
 * modelo no ve ni puede modificar. Así las cifras de la pantalla salen de los services y no del texto del modelo.
 */
public final class ResponseData {
    /** Clave del {@code ResponseData} dentro del contexto de las tools. */
    public static final String CONTEXT_KEY = "responseData";
    /** Clave del equipo abierto en la aplicación ({@code Integer}); se omite si no hay ninguno. */
    public static final String TEAM_ID_KEY = "teamId";

    private List<TeamMatchInfo> teams;
    private List<ProblemInfo> problems;
    private ChartData chart;
    private ActionInfo action;

    public static ResponseData from(ToolContext context) {
        if (context.getContext().get(CONTEXT_KEY) instanceof ResponseData data) {
            return data;
        }
        throw new IllegalStateException("The tool context has no ResponseData");
    }

    public void setTeams(List<TeamMatchInfo> teams) {
        this.teams = List.copyOf(teams);
    }

    public void setProblems(List<ProblemInfo> problems) {
        this.problems = List.copyOf(problems);
    }

    public void setChart(ChartData chart) {
        this.chart = chart;
    }

    /** Solo puede quedar una acción pendiente de confirmación por mensaje. */
    public void setAction(ActionInfo action) {
        if (this.action != null) {
            throw new IllegalStateException("There is already an action awaiting confirmation");
        }
        this.action = action;
    }

    public AgentResponse toResponse(String conversationId, String agent, String message) {
        return new AgentResponse(conversationId, agent, message, teams, problems, chart, action);
    }
}

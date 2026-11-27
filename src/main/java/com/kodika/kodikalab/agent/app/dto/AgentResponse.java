package com.kodika.kodikalab.agent.app.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Respuesta de {@code /agent/chat}. Los bloques opcionales solo aparecen cuando una tool aportó datos reales.
 * Contrato: {@code docs/agent/contract.md}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentResponse(String conversationId, String agent, String message, List<TeamMatchInfo> teams,
                            List<ProblemInfo> problems, ChartData chart, ActionInfo action) {
}

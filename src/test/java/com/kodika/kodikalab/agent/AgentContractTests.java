package com.kodika.kodikalab.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kodika.kodikalab.agent.app.dto.ActionInfo;
import com.kodika.kodikalab.agent.app.dto.AgentResponse;
import com.kodika.kodikalab.agent.app.dto.ResponseData;
import com.kodika.kodikalab.agent.app.dto.TeamMatchInfo;
import com.kodika.kodikalab.agent.core.AgentDefinition;
import com.kodika.kodikalab.users.Role;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Pins the parts of the contract described in docs/agent/contract.md that the frontend relies on. */
class AgentContractTests {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void aTextOnlyAnswerOmitsEveryOptionalBlock() {
        String json = mapper.writeValueAsString(new ResponseData().toResponse("c-1", "info", "Hola"));

        assertThat(json).isEqualTo("{\"conversationId\":\"c-1\",\"agent\":\"info\",\"message\":\"Hola\"}");
    }

    @Test
    void anExactTeamMatchKeepsAnEmptyListOfDifferences() {
        ResponseData data = new ResponseData();
        data.setTeams(List.of(new TeamMatchInfo(7, "Algoritmos", "Intermedio", "Sábados", List.of())));

        String json = mapper.writeValueAsString(data.toResponse("c-1", "teams", "Encontré 1 grupo"));

        assertThat(json).contains("\"teams\":[{").contains("\"groupId\":7").contains("\"differences\":[]")
                .doesNotContain("\"problems\"").doesNotContain("\"chart\"").doesNotContain("\"action\"");
    }

    @Test
    void onlyOneActionCanBeAwaitingConfirmation() {
        ResponseData data = new ResponseData();
        ActionInfo action = new ActionInfo("a-1", "JOIN_GROUP", "Unirte a Algoritmos", OffsetDateTime.now());
        data.setAction(action);

        assertThatThrownBy(() -> data.setAction(action)).isInstanceOf(IllegalStateException.class);
        AgentResponse response = data.toResponse("c-1", "actions", "¿Confirmas?");
        assertThat(response.action()).isEqualTo(action);
    }

    @Test
    void aDefinitionSaysWhichRolesMayUseTheAgent() {
        AgentDefinition insights = new AgentDefinition("insights", "Team performance", Set.of(Role.COACH), false,
                List.of());

        assertThat(insights.isAllowedFor(Role.COACH)).isTrue();
        assertThat(insights.isAllowedFor(Role.PRACTICANTE)).isFalse();
        assertThat(insights.isAllowedFor(null)).isFalse();
    }

    @Test
    void aDefinitionWithoutNameDescriptionOrRolesIsRejected() {
        assertThatThrownBy(() -> new AgentDefinition(" ", "d", Set.of(Role.COACH), false, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AgentDefinition("a", " ", Set.of(Role.COACH), false, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AgentDefinition("a", "d", Set.of(), false, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

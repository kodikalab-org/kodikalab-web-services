package com.kodika.kodikalab.agent.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kodika.kodikalab.agent.core.conversation.ChatRequest;
import com.kodika.kodikalab.agent.core.routing.RoutingWorkflow;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.ServiceUnavailableException;
import com.kodika.kodikalab.common.exception.TooManyRequestsException;
import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserStatus;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.ObjectProvider;

class AgentServiceTests {
    /** Any object with a tool makes an agent available. */
    static class SampleTools {
        @Tool(description = "Sample")
        public String sample() {
            return "ok";
        }
    }

    private final CurrentUserResolver currentUser = mock(CurrentUserResolver.class);
    private final ChatModel model = mock(ChatModel.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<ChatModel> models = mock(ObjectProvider.class);
    private final User user = mock(User.class);
    private final List<AgentDefinition> agents = List.of(
            new AgentDefinition("insights", "Team performance and recommendations", Set.of(Role.COACH), false,
                    List.of(new SampleTools())),
            new AgentDefinition("teams", "Search groups", Set.of(Role.PRACTICANTE), false, List.of()));
    private AgentService service;

    @BeforeEach
    void activeCoach() {
        when(user.getId()).thenReturn(5);
        when(user.getRole()).thenReturn(Role.COACH);
        when(user.getStatus()).thenReturn(UserStatus.ACTIVO);
        when(currentUser.currentUser()).thenReturn(user);
        when(models.getIfAvailable()).thenReturn(model);
        when(model.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatClient.Builder> clients = mock(ObjectProvider.class);
        when(clients.getObject()).thenAnswer(invocation -> ChatClient.builder(model));
        service = new AgentService(currentUser, new RoutingWorkflow(), agents, models, clients,
                new AgentRateLimiter(Clock.systemUTC()));
    }

    @Test
    void routesTheMessageAndAnswersWithTheChosenAgent() {
        modelAnswers("{\"agent\":\"insights\"}", "Tu equipo debe reforzar grafos");

        var response = service.chat(request("¿Qué practicamos?"));

        assertThat(response.agent()).isEqualTo("insights");
        assertThat(response.message()).isEqualTo("Tu equipo debe reforzar grafos");
        assertThat(response.conversationId()).isNotBlank();
        var prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(2)).call(prompts.capture());
        assertThat(prompts.getAllValues().getLast().getSystemMessage().getText()).contains("KodikaLab")
                .contains("Hay un equipo abierto");
    }

    @Test
    void anAgentNameThatDoesNotExistRunsNothing() {
        modelAnswers("{\"agent\":\"drop-database\"}");

        var response = service.chat(request("hola"));

        assertThat(response.agent()).isEqualTo("none");
        verify(model, times(1)).call(any(Prompt.class));
    }

    @Test
    void anUnreadableRouterAnswerRunsNothing() {
        modelAnswers("I would pick the insights one");

        assertThat(service.chat(request("hola")).agent()).isEqualTo("none");
        verify(model, times(1)).call(any(Prompt.class));
    }

    @Test
    void theRoleIsCheckedByCodeBeforeTheAgentRuns() {
        modelAnswers("{\"agent\":\"teams\"}");

        var response = service.chat(request("busco un grupo"));

        assertThat(response.message()).contains("no está disponible para tu rol");
        verify(model, times(1)).call(any(Prompt.class));
    }

    @Test
    void anAgentWithoutToolsSaysItIsNotAvailableYet() {
        when(user.getRole()).thenReturn(Role.PRACTICANTE);
        modelAnswers("{\"agent\":\"teams\"}");

        assertThat(service.chat(request("busco un grupo")).message()).contains("todavía no está disponible");
        verify(model, times(1)).call(any(Prompt.class));
    }

    @Test
    void anInactiveAccountNeverReachesTheModel() {
        when(user.getStatus()).thenReturn(UserStatus.SUSPENDIDO);

        assertThatThrownBy(() -> service.chat(request("hola"))).isInstanceOf(ForbiddenException.class);
        verify(model, never()).call(any(Prompt.class));
    }

    @Test
    void withAiDisabledItAnswersServiceUnavailable() {
        when(models.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> service.chat(request("hola"))).isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void aProviderFailureBecomesASafeServiceUnavailable() {
        when(model.call(any(Prompt.class))).thenThrow(new IllegalStateException("401 invalid key gsk_secret"));

        assertThatThrownBy(() -> service.chat(request("hola"))).isInstanceOf(ServiceUnavailableException.class)
                .hasMessageNotContaining("gsk_secret");
    }

    @Test
    void eachUserHasALimitOfMessagesPerMinute() {
        modelAnswers("{\"agent\":\"none\"}");
        for (int i = 0; i < AgentRateLimiter.MAX_PER_WINDOW; i++) {
            service.chat(request("hola"));
        }

        assertThatThrownBy(() -> service.chat(request("hola"))).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void theConversationIdIsKeptWhenSent() {
        modelAnswers("{\"agent\":\"none\"}");
        String id = "7f2c1d9e-5b0a-4c3f-9a51-2f6f0d1b8e44";

        assertThat(service.chat(new ChatRequest("hola", id, null)).conversationId()).isEqualTo(id);
    }

    private static ChatRequest request(String message) {
        return new ChatRequest(message, null, 7);
    }

    private void modelAnswers(String first, String... then) {
        var stub = when(model.call(any(Prompt.class))).thenReturn(answer(first));
        for (String text : then) {
            stub = stub.thenReturn(answer(text));
        }
    }

    private static ChatResponse answer(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}

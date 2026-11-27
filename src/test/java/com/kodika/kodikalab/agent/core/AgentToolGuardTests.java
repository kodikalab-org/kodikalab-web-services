package com.kodika.kodikalab.agent.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kodika.kodikalab.agent.core.conversation.ChatRequest;
import com.kodika.kodikalab.agent.core.routing.RoutingWorkflow;
import com.kodika.kodikalab.common.exception.ServiceUnavailableException;
import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserStatus;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Checks that the guards configured in application.yaml really apply to the agent. No provider is called. */
class AgentToolGuardTests {
    /** Stops the scripted model so a missing limit fails the test instead of hanging it. */
    private static final int GIVE_UP = 20;

    static class LoopingTools {
        final AtomicInteger runs = new AtomicInteger();
        RuntimeException failure;

        @Tool(description = "Sample")
        public String sample() {
            runs.incrementAndGet();
            if (failure != null) {
                throw failure;
            }
            return "ok";
        }
    }

    private final ChatModel model = mock(ChatModel.class);
    private final AtomicInteger modelCalls = new AtomicInteger();
    private final LoopingTools tools = new LoopingTools();
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(ToolCallingAutoConfiguration.class,
                    ChatClientAutoConfiguration.class))
            .withBean(ChatModel.class, () -> model);

    @Test
    void aModelThatKeepsAskingForToolsIsStopped() {
        modelAlwaysAsksForTheTool();

        runner.run(context -> {
            var response = service(context).chat(new ChatRequest("hola", null, null));
            assertThat(tools.runs.get()).isEqualTo(5);
            // The library's internal English text must not reach the user.
            assertThat(response.message()).contains("demasiados pasos").doesNotContain("limit");
        });
    }

    @Test
    void anUnexpectedToolErrorIsNeverSentBackToTheModel() {
        modelAlwaysAsksForTheTool();
        tools.failure = new IllegalStateException("internal database detail");

        runner.run(context -> {
            assertThatThrownBy(() -> service(context).chat(new ChatRequest("hola", null, null)))
                    .isInstanceOf(ServiceUnavailableException.class)
                    .hasMessageNotContaining("internal database detail");
            // One call for routing and one that asked for the tool: nothing after the failure.
            assertThat(modelCalls.get()).isEqualTo(2);
        });
    }

    @SuppressWarnings("unchecked")
    private AgentService service(AssertableApplicationContext context) {
        var currentUser = mock(CurrentUserResolver.class);
        var user = mock(User.class);
        when(user.getId()).thenReturn(5);
        when(user.getRole()).thenReturn(Role.COACH);
        when(user.getStatus()).thenReturn(UserStatus.ACTIVO);
        when(currentUser.currentUser()).thenReturn(user);
        ObjectProvider<ChatModel> models = mock(ObjectProvider.class);
        when(models.getIfAvailable()).thenReturn(model);
        var agent = new AgentDefinition("insights", "Team performance", Set.of(Role.COACH), false, List.of(tools));
        return new AgentService(currentUser, new RoutingWorkflow(), List.of(agent), models,
                context.getBeanProvider(ChatClient.Builder.class), new AgentRateLimiter(Clock.systemUTC()));
    }

    private void modelAlwaysAsksForTheTool() {
        when(model.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            int call = modelCalls.incrementAndGet();
            if (call == 1) {
                return answer(new AssistantMessage("{\"agent\":\"insights\"}"));
            }
            if (call > GIVE_UP) {
                return answer(new AssistantMessage("gave up"));
            }
            return answer(AssistantMessage.builder().content("")
                    .toolCalls(List.of(new AssistantMessage.ToolCall("call-" + call, "function", "sample", "{}")))
                    .build());
        });
    }

    private static ChatResponse answer(AssistantMessage message) {
        return new ChatResponse(List.of(new Generation(message)));
    }
}

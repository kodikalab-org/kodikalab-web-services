package com.kodika.kodikalab.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kodika.kodikalab.agent.app.AgentCatalog;
import com.kodika.kodikalab.agent.app.tools.InsightTools;
import com.kodika.kodikalab.agent.core.AgentDefinition;
import com.kodika.kodikalab.agent.core.AgentRateLimiter;
import com.kodika.kodikalab.agent.core.AgentService;
import com.kodika.kodikalab.agent.core.conversation.ChatRequest;
import com.kodika.kodikalab.agent.core.routing.RoutingWorkflow;
import com.kodika.kodikalab.analytics.TeamTopicReportService;
import com.kodika.kodikalab.analytics.dto.TeamTopicReportResponse;
import com.kodika.kodikalab.analytics.dto.TeamTopicReportResponse.TopicPerformance;
import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.recommendations.ProblemRecommendation;
import com.kodika.kodikalab.recommendations.ProblemRecommendationService;
import com.kodika.kodikalab.recommendations.RecommendationResult;
import com.kodika.kodikalab.teams.studygroup.StudyGroupService;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.webclient.autoconfigure.WebClientAutoConfiguration;

/**
 * Routing and tool use with a real model, through the same {@code AgentService} and catalog the application uses. The
 * domain services are mocked, so no database is involved. Uses provider quota; run with {@code -Dai.smoke=ai-groq}.
 */
@EnabledIfSystemProperty(named = "ai.smoke", matches = "ai-(groq|openai)")
class AgentLiveTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class,
                    WebClientAutoConfiguration.class, ToolCallingAutoConfiguration.class,
                    OpenAiChatAutoConfiguration.class, ChatClientAutoConfiguration.class))
            .withSystemProperties("spring.profiles.active=" + System.getProperty("ai.smoke"));

    @Test
    void eachKindOfMessageReachesItsAgent() {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("¿En qué temas está más débil mi equipo?", "insights");
        expected.put("Busco un grupo para entrenar los sábados, nivel intermedio", "teams");
        expected.put("Crea un grupo llamado Los Recursivos", "actions");
        expected.put("¿Cuál es la política de privacidad de KodikaLab?", "info");
        expected.put("¿Quién ganó el último mundial de fútbol?", "none");

        runner.run(context -> {
            List<AgentDefinition> agents = agents(mock(InsightTools.class));
            var routing = new RoutingWorkflow();
            List<String> wrong = new ArrayList<>();
            expected.forEach((message, agent) -> {
                String chosen = routing.route(context.getBean(ChatModel.class), message, agents)
                        .map(AgentDefinition::name).orElse("none");
                System.out.println("Route: " + chosen + " <- " + message);
                if (!chosen.equals(agent)) {
                    wrong.add(message + " -> " + chosen);
                }
            });
            assertThat(wrong).isEmpty();
        });
    }

    @Test
    void aCoachAskingForProblemsGetsRecommendationCards() {
        var currentUser = mock(CurrentUserResolver.class);
        var coach = mock(User.class);
        when(coach.getId()).thenReturn(5);
        when(coach.getRole()).thenReturn(Role.COACH);
        when(coach.getStatus()).thenReturn(UserStatus.ACTIVO);
        when(currentUser.currentUser()).thenReturn(coach);
        var recommendations = mock(ProblemRecommendationService.class);
        when(recommendations.recommend(eq(7), any())).thenReturn(new RecommendationResult(
                new TeamTopicReportResponse(7, "m", "c", "e", 3, 0, List.of(new TopicPerformance(1,
                        "Programación dinámica", 4, 1, 3, 1, 0, new BigDecimal("25.00"), true))),
                List.of(new ProblemRecommendation(11, "Knapsack", "https://example.test/11", "Programación dinámica",
                        "1200", ProblemRecommendation.Source.CATALOG, "motivo")),
                1100, 1400, List.of()));
        var tools = new InsightTools(currentUser, mock(StudyGroupService.class), mock(TeamTopicReportService.class),
                recommendations);

        runner.run(context -> {
            var service = new AgentService(currentUser, new RoutingWorkflow(), agents(tools),
                    context.getBeanProvider(ChatModel.class), context.getBeanProvider(ChatClient.Builder.class),
                    new AgentRateLimiter());

            var response = service.chat(new ChatRequest("Recomiéndame problemas para que practique mi equipo", null, 7));

            System.out.println("Answer: " + response.message());
            verify(recommendations).recommend(eq(7), any());
            assertThat(response.agent()).isEqualTo("insights");
            // Tool results must not carry instructions the model could repeat to the user.
            assertThat(response.message()).doesNotContain("al coach").doesNotContain("La aplicación");
            assertThat(response.problems()).extracting(problem -> problem.title()).containsExactly("Knapsack");
            assertThat(response.chart()).isNotNull();
        });
    }

    private static List<AgentDefinition> agents(InsightTools tools) {
        var catalog = new AgentCatalog();
        return List.of(catalog.insightsAgent(tools), catalog.teamsAgent(), catalog.actionsAgent(), catalog.infoAgent());
    }
}

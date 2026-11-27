package com.kodika.kodikalab.agent.app.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kodika.kodikalab.agent.app.dto.ResponseData;
import com.kodika.kodikalab.analytics.TeamTopicReportService;
import com.kodika.kodikalab.analytics.dto.TeamTopicReportResponse;
import com.kodika.kodikalab.analytics.dto.TeamTopicReportResponse.TopicPerformance;
import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.recommendations.ProblemRecommendationService;
import com.kodika.kodikalab.teams.studygroup.StudyGroupService;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.webclient.autoconfigure.WebClientAutoConfiguration;

/**
 * Checks that a real model asks for the tool on its own. The services are mocked, so no database is involved. It uses
 * provider quota and only runs with {@code -Dai.smoke=ai-groq} or {@code -Dai.smoke=ai-openai}.
 */
@EnabledIfSystemProperty(named = "ai.smoke", matches = "ai-(groq|openai)")
class InsightToolsLiveTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class,
                    WebClientAutoConfiguration.class, ToolCallingAutoConfiguration.class,
                    OpenAiChatAutoConfiguration.class))
            .withSystemProperties("spring.profiles.active=" + System.getProperty("ai.smoke"));

    @Test
    void theModelCallsTheToolAndAnswersWithItsResult() {
        var currentUser = mock(CurrentUserResolver.class);
        var reports = mock(TeamTopicReportService.class);
        var coach = mock(User.class);
        when(coach.getRole()).thenReturn(Role.COACH);
        when(coach.getStatus()).thenReturn(UserStatus.ACTIVO);
        when(currentUser.currentUser()).thenReturn(coach);
        when(reports.getReport(any())).thenReturn(new TeamTopicReportResponse(7, "m", "c", "e", 3, 0, List.of(
                new TopicPerformance(1, "Programación dinámica", 4, 1, 3, 1, 0, new BigDecimal("25.00"), true))));
        var tools = new InsightTools(currentUser, mock(StudyGroupService.class), reports,
                mock(ProblemRecommendationService.class));
        var data = new ResponseData();

        runner.run(context -> {
            String answer = ChatClient.create(context.getBean(ChatModel.class)).prompt()
                    .system("Eres el asistente de un coach. Usa las tools para obtener datos. Responde en español.")
                    .user("¿En qué temas está más débil mi equipo?")
                    .tools(tools)
                    .toolContext(Map.of(ResponseData.CONTEXT_KEY, data, ResponseData.TEAM_ID_KEY, 7))
                    .call().content();
            System.out.println("Answer: " + answer);
            verify(reports).getReport(7);
            assertThat(answer).containsIgnoringCase("din");
            assertThat(data.toResponse("c-1", "insights", answer).chart()).isNotNull();
        });
    }
}

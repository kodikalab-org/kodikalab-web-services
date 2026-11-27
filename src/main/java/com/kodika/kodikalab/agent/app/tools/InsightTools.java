package com.kodika.kodikalab.agent.app.tools;

import com.kodika.kodikalab.agent.app.dto.ChartData;
import com.kodika.kodikalab.agent.app.dto.ProblemInfo;
import com.kodika.kodikalab.agent.app.dto.ResponseData;
import com.kodika.kodikalab.agent.app.tools.ToolOutcome.Status;
import com.kodika.kodikalab.analytics.TeamTopicReportService;
import com.kodika.kodikalab.analytics.TopicReportDataException;
import com.kodika.kodikalab.analytics.dto.TeamTopicReportResponse;
import com.kodika.kodikalab.analytics.dto.TeamTopicReportResponse.TopicPerformance;
import com.kodika.kodikalab.common.exception.BadRequestException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.recommendations.ProblemRecommendation;
import com.kodika.kodikalab.recommendations.ProblemRecommendationService;
import com.kodika.kodikalab.recommendations.RecommendationCriteria;
import com.kodika.kodikalab.recommendations.RecommendationResult;
import com.kodika.kodikalab.teams.studygroup.StudyGroupService;
import com.kodika.kodikalab.teams.studygroup.dto.MyTeamResponse;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserStatus;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Tools del agente {@code insights} y ejemplo de referencia para las demás:
 * <ol>
 *   <li>El modelo solo aporta datos de la conversación (aquí, el nombre del equipo). La identidad sale de la sesión.</li>
 *   <li>La tool no repite reglas de negocio ni permisos: llama al service que ya los aplica.</li>
 *   <li>Las cifras van a {@link ResponseData}; al modelo solo se le devuelve un {@link ToolOutcome}.</li>
 *   <li>Los errores esperados se devuelven como resultado, con el mensaje seguro del service.</li>
 * </ol>
 */
@Component
public class InsightTools {
    private final CurrentUserResolver currentUserResolver;
    private final StudyGroupService groupService;
    private final TeamTopicReportService reportService;
    private final ProblemRecommendationService recommendationService;

    public InsightTools(CurrentUserResolver currentUserResolver, StudyGroupService groupService,
                        TeamTopicReportService reportService, ProblemRecommendationService recommendationService) {
        this.currentUserResolver = currentUserResolver;
        this.groupService = groupService;
        this.reportService = reportService;
        this.recommendationService = recommendationService;
    }

    @Tool(name = "getWeakTopics", description = "Finds the topics where one of the coach's teams has the lowest "
            + "coverage. Use it when the coach asks about weaknesses or what the team should practice. The "
            + "application shows the figures in a chart, so do not state any numbers.")
    public ToolOutcome getWeakTopics(
            @ToolParam(required = false, description = "Team name exactly as the user wrote it. Omit it when the "
                    + "user did not name a team.") String teamName,
            ToolContext context) {
        return onTeam(teamName, context, teamId -> report(reportService.getReport(teamId), ResponseData.from(context)));
    }

    @Tool(name = "recommendProblems", description = "Recommends practice problems for one of the coach's teams, for "
            + "its weakest topics or for a topic the coach names. Use it when the coach asks what problems to solve "
            + "or assign. The application shows the problems and why they were chosen, so do not list or invent them.")
    public ToolOutcome recommendProblems(
            @ToolParam(required = false, description = "Team name exactly as the user wrote it. Omit it when the "
                    + "user did not name a team.") String teamName,
            @ToolParam(required = false, description = "Topic the user asked for. Omit it to use the weakest "
                    + "topics.") String topic,
            @ToolParam(required = false, description = "Lowest difficulty rating, only if the user asked for "
                    + "one.") Integer minDifficulty,
            @ToolParam(required = false, description = "Highest difficulty rating, only if the user asked for "
                    + "one.") Integer maxDifficulty,
            ToolContext context) {
        var criteria = new RecommendationCriteria(minDifficulty, maxDifficulty, topic);
        return onTeam(teamName, context, teamId -> recommendation(recommendationService.recommend(teamId, criteria),
                ResponseData.from(context)));
    }

    private ToolOutcome recommendation(RecommendationResult result, ResponseData data) {
        if (result.problems().isEmpty()) {
            return new ToolOutcome(Status.INSUFFICIENT_DATA, "No hay problemas que recomendar con sustento",
                    result.notices());
        }
        chart(result.report(), data);
        data.setProblems(result.problems().stream().map(InsightTools::card).toList());
        List<String> details = new ArrayList<>(result.problems().stream().map(ProblemRecommendation::topic)
                .distinct().toList());
        details.addAll(result.notices());
        return new ToolOutcome(Status.OK, "Propuestas de problemas preparadas para los temas del detalle", details);
    }

    private static ProblemInfo card(ProblemRecommendation problem) {
        return new ProblemInfo(problem.problemId(), problem.title(), problem.url(), problem.topic(),
                problem.difficulty(), problem.reason(), problem.source().name());
    }

    /** Checks the role, finds which team is meant and turns the expected failures into outcomes. */
    private ToolOutcome onTeam(String teamName, ToolContext context, Function<Integer, ToolOutcome> action) {
        User user = currentUserResolver.currentUser();
        if (user.getStatus() != UserStatus.ACTIVO || user.getRole() != Role.COACH) {
            return ToolOutcome.of(Status.DENIED, "Solo un coach activo puede consultar el reporte de temas");
        }
        Integer teamId;
        if (teamName != null && !teamName.isBlank()) {
            // A named team wins over the open one; an unknown name must never fall back to another team.
            List<MyTeamResponse> own = groupService.findMyTeams();
            List<MyTeamResponse> matches = matching(own, teamName);
            if (matches.size() != 1) {
                return askForTeam(own);
            }
            teamId = matches.getFirst().groupId();
        } else if (context.getContext().get(ResponseData.TEAM_ID_KEY) instanceof Integer openTeam) {
            teamId = openTeam;
        } else {
            return askForTeam(groupService.findMyTeams());
        }
        try {
            return action.apply(teamId);
        } catch (ForbiddenException | NotFoundException | BadRequestException exception) {
            return ToolOutcome.of(Status.DENIED, exception.getMessage());
        } catch (TopicReportDataException exception) {
            return new ToolOutcome(Status.INSUFFICIENT_DATA, exception.getMessage(),
                    List.copyOf(exception.getErrors().values()));
        }
    }

    private ToolOutcome report(TeamTopicReportResponse report, ResponseData data) {
        List<TopicPerformance> topics = report.topics();
        if (topics.isEmpty()) {
            return ToolOutcome.of(Status.INSUFFICIENT_DATA, "El equipo todavía no tiene temas para analizar");
        }
        chart(report, data);
        List<String> weakest = topics.stream().filter(TopicPerformance::lowestCoverage)
                .map(TopicPerformance::topicName).toList();
        return new ToolOutcome(Status.OK, "Temas con la menor cobertura del equipo", weakest);
    }

    private static void chart(TeamTopicReportResponse report, ResponseData data) {
        data.setChart(new ChartData("Cobertura por tema", "%",
                report.topics().stream().map(TopicPerformance::topicName).toList(),
                report.topics().stream().map(TopicPerformance::coveragePercentage).toList()));
    }

    private static ToolOutcome askForTeam(List<MyTeamResponse> own) {
        if (own.isEmpty()) {
            return ToolOutcome.of(Status.INSUFFICIENT_DATA, "Todavía no tienes equipos a tu cargo");
        }
        return new ToolOutcome(Status.NEEDS_TEAM, "Falta saber de cuál de estos equipos se trata",
                own.stream().map(MyTeamResponse::name).toList());
    }

    /** Exact name first (ignoring case and accents); otherwise every team whose name contains the text. */
    private static List<MyTeamResponse> matching(List<MyTeamResponse> teams, String teamName) {
        String wanted = fold(teamName);
        List<MyTeamResponse> exact = new ArrayList<>();
        List<MyTeamResponse> partial = new ArrayList<>();
        for (MyTeamResponse team : teams) {
            String name = fold(team.name());
            if (name.equals(wanted)) {
                exact.add(team);
            } else if (name.contains(wanted)) {
                partial.add(team);
            }
        }
        return exact.isEmpty() ? partial : exact;
    }

    private static String fold(String value) {
        return value == null ? "" : Normalizer.normalize(value.strip(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}

package com.kodika.kodikalab.analytics;

import com.kodika.kodikalab.analytics.dto.TeamTopicReportResponse;
import com.kodika.kodikalab.common.exception.BadRequestException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.competitions.competition.CompetitionStatus;
import com.kodika.kodikalab.competitions.competitionproblem.CompetitionProblemService;
import com.kodika.kodikalab.competitions.competitionproblem.dto.TeamAssignedProblem;
import com.kodika.kodikalab.competitions.problemresolution.ProblemResolutionService;
import com.kodika.kodikalab.problems.problemtopic.ProblemTopicService;
import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.teams.groupmembership.GroupMembershipService;
import com.kodika.kodikalab.teams.studygroup.StudyGroupService;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.UserStatus;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeamTopicReportService {
    private final CurrentUserResolver currentUserResolver;
    private final StudyGroupService groupService;
    private final GroupMembershipService membershipService;
    private final CompetitionProblemService assignmentService;
    private final ProblemResolutionService resolutionService;
    private final ProblemTopicService topicService;
    private final TopicCoverageCalculator calculator;

    public TeamTopicReportService(CurrentUserResolver currentUserResolver, StudyGroupService groupService,
                                  GroupMembershipService membershipService, CompetitionProblemService assignmentService,
                                  ProblemResolutionService resolutionService, ProblemTopicService topicService,
                                  TopicCoverageCalculator calculator) {
        this.currentUserResolver = currentUserResolver;
        this.groupService = groupService;
        this.membershipService = membershipService;
        this.assignmentService = assignmentService;
        this.resolutionService = resolutionService;
        this.topicService = topicService;
        this.calculator = calculator;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public TeamTopicReportResponse getReport(Integer teamId) {
        var requester = currentUserResolver.currentUser();
        if (requester.getStatus() != UserStatus.ACTIVO || requester.getRole() != Role.COACH) {
            throw new ForbiddenException("Solo un coach activo puede consultar el reporte de temas");
        }
        if (teamId == null || teamId <= 0) {
            throw new BadRequestException("El identificador del equipo debe ser un entero positivo");
        }
        var team = groupService.findSummaryById(teamId)
                .orElseThrow(() -> new NotFoundException("El equipo no existe"));
        if (!teamId.equals(team.teamId()) || team.coachUserId() == null || team.coachUserId() <= 0) {
            throw new TopicReportDataException("La información del equipo está incompleta",
                    Map.of("team", "El equipo o su coach responsable no está disponible"));
        }
        if (!team.coachUserId().equals(requester.getId())) {
            throw new ForbiddenException("No tiene autorización para consultar el reporte de este equipo");
        }
        var members = membershipService.findMembersByTeamId(teamId);
        var assignments = assignmentService.findAssignedProblemsByTeamId(teamId);
        if (assignments == null) {
            throw new TopicReportDataException("La información necesaria no está disponible",
                    Map.of("assignments", "No se pudieron obtener los problemas asignados"));
        }
        Set<Integer> problems = assignments.stream().filter(a -> a != null && a.status() == CompetitionStatus.FINALIZADA)
                .map(TeamAssignedProblem::problemId).filter(id -> id != null && id > 0).collect(Collectors.toSet());
        var topics = topicService.findTopicsByProblemIds(problems);
        var resolutions = resolutionService.findResolutionsByTeamId(teamId);
        return calculator.calculate(teamId, members, assignments, resolutions, topics);
    }
}

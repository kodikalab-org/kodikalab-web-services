package com.kodika.kodikalab.analytics;

import com.kodika.kodikalab.analytics.dto.TopicProgressResponse;
import com.kodika.kodikalab.common.exception.BadRequestException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.competitions.competitionproblem.CompetitionProblemService;
import com.kodika.kodikalab.competitions.competitionproblem.dto.TeamAssignedProblem;
import com.kodika.kodikalab.competitions.problemresolution.ProblemResolutionService;
import com.kodika.kodikalab.problems.problemtopic.ProblemTopicService;
import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.teams.groupmembership.GroupMembershipService;
import com.kodika.kodikalab.teams.groupmembership.MembershipStatus;
import com.kodika.kodikalab.teams.groupmembership.dto.GroupMemberData;
import com.kodika.kodikalab.teams.studygroup.StudyGroupService;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.UserStatus;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** US-10: el practicante consulta su propio progreso por tema en uno de sus equipos. */
@Service
public class TopicProgressService {
    private final CurrentUserResolver currentUserResolver;
    private final StudyGroupService groupService;
    private final GroupMembershipService membershipService;
    private final CompetitionProblemService assignmentService;
    private final ProblemResolutionService resolutionService;
    private final ProblemTopicService topicService;
    private final TopicProgressCalculator calculator;

    public TopicProgressService(CurrentUserResolver currentUserResolver, StudyGroupService groupService,
                                GroupMembershipService membershipService, CompetitionProblemService assignmentService,
                                ProblemResolutionService resolutionService, ProblemTopicService topicService,
                                TopicProgressCalculator calculator) {
        this.currentUserResolver = currentUserResolver;
        this.groupService = groupService;
        this.membershipService = membershipService;
        this.assignmentService = assignmentService;
        this.resolutionService = resolutionService;
        this.topicService = topicService;
        this.calculator = calculator;
    }

    /** Una sola lectura consistente: las consultas comparten el mismo estado de la base de datos. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public TopicProgressResponse getProgress(Integer teamId) {
        var requester = currentUserResolver.currentUser();
        if (requester.getStatus() != UserStatus.ACTIVO || requester.getRole() != Role.PRACTICANTE
                || requester.getId() == null || requester.getId() <= 0) {
            throw new ForbiddenException("Solo un practicante activo puede consultar su progreso por tema");
        }
        if (teamId == null || teamId <= 0) {
            throw new BadRequestException("El identificador del equipo debe ser un entero positivo");
        }
        groupService.findSummaryById(teamId).orElseThrow(() -> new NotFoundException("El equipo no existe"));
        GroupMemberData member = membershipService.findMembersByTeamId(teamId).stream()
                .filter(m -> m != null && requester.getId().equals(m.userId()) && teamId.equals(m.teamId())
                        && m.status() == MembershipStatus.ACTIVO)
                .findFirst().orElseThrow(() -> new ForbiddenException(
                        "Seleccione un contexto de equipo válido: necesita una membresía activa propia"));
        var assignments = assignmentService.findAssignedProblemsByTeamId(teamId);
        if (assignments == null) {
            throw new TopicProgressDataException("La información necesaria no está disponible",
                    Map.of("assignments", "No se pudieron obtener los problemas asignados"));
        }
        Set<Integer> problems = assignments.stream().filter(a -> a != null)
                .map(TeamAssignedProblem::problemId).filter(id -> id != null && id > 0).collect(Collectors.toSet());
        var topics = topicService.findTopicsByProblemIds(problems);
        var attempts = resolutionService.findAttemptsByMembershipId(member.membershipId());
        return calculator.calculate(teamId, member, assignments, attempts, topics);
    }
}

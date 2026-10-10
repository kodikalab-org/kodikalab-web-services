package com.kodika.kodikalab.analytics;

import com.kodika.kodikalab.analytics.dto.TeamTopicReportResponse;
import com.kodika.kodikalab.analytics.dto.TeamTopicReportResponse.TopicPerformance;
import com.kodika.kodikalab.competitions.competition.CompetitionStatus;
import com.kodika.kodikalab.competitions.competitionproblem.dto.TeamAssignedProblem;
import com.kodika.kodikalab.competitions.problemresolution.Verdict;
import com.kodika.kodikalab.competitions.problemresolution.dto.TeamResolutionData;
import com.kodika.kodikalab.problems.problemtopic.dto.ProblemTopicData;
import com.kodika.kodikalab.teams.groupmembership.MembershipStatus;
import com.kodika.kodikalab.teams.groupmembership.dto.GroupMemberData;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class TopicCoverageCalculator {
    public TeamTopicReportResponse calculate(Integer teamId, List<GroupMemberData> members,
                                             List<TeamAssignedProblem> assignments,
                                             List<TeamResolutionData> resolutions, List<ProblemTopicData> topics) {
        requireId(teamId, "teamId");
        requireCollection(members, "members");
        requireCollection(assignments, "assignments");
        requireCollection(resolutions, "resolutions");
        requireCollection(topics, "topics");
        Map<Integer, GroupMemberData> memberships = validateMembers(teamId, members);
        int activeMembers = (int) members.stream().filter(m -> m.status() == MembershipStatus.ACTIVO).count();
        if (activeMembers == 0) {
            throw invalid("members", "No hay integrantes activos para analizar el rendimiento");
        }
        Map<Integer, TeamAssignedProblem> assigned = validateAssignments(teamId, assignments);
        Set<Integer> problems = new HashSet<>();
        assigned.values().stream().filter(a -> a.status() == CompetitionStatus.FINALIZADA)
                .forEach(a -> problems.add(a.problemId()));
        if (problems.isEmpty()) {
            throw invalid("assignments", "No hay problemas asignados en competencias FINALIZADA");
        }
        Map<Integer, TopicTotals> totals = validateTopics(problems, topics);
        ResolutionSummary summary = consolidate(teamId, memberships, assigned, resolutions);
        if (!summary.hasDefinitiveResolution) {
            throw invalid("resolutions", "No hay resoluciones definitivas de integrantes activos en competencias "
                    + "FINALIZADA; resoluciones pendientes: " + summary.pendingCount);
        }
        return response(teamId, activeMembers, totals, summary);
    }

    private Map<Integer, GroupMemberData> validateMembers(Integer teamId, List<GroupMemberData> members) {
        Map<Integer, GroupMemberData> result = new HashMap<>();
        Set<Integer> users = new HashSet<>();
        for (int index = 0; index < members.size(); index++) {
            GroupMemberData member = members.get(index);
            String field = "members[" + index + "]";
            if (member == null || member.status() == null) {
                throw invalid(field, "La membresía está incompleta");
            }
            requireId(member.membershipId(), field + ".membershipId");
            requireId(member.userId(), field + ".userId");
            if (!teamId.equals(member.teamId())) {
                throw invalid(field + ".teamId", "La membresía no pertenece al equipo consultado");
            }
            if (result.putIfAbsent(member.membershipId(), member) != null || !users.add(member.userId())) {
                throw invalid(field, "La membresía o el integrante está duplicado");
            }
        }
        return result;
    }

    private Map<Integer, TeamAssignedProblem> validateAssignments(Integer teamId, List<TeamAssignedProblem> assignments) {
        Map<Integer, TeamAssignedProblem> result = new HashMap<>();
        Map<Integer, CompetitionStatus> statuses = new HashMap<>();
        for (int index = 0; index < assignments.size(); index++) {
            TeamAssignedProblem assignment = assignments.get(index);
            String field = "assignments[" + index + "]";
            if (assignment == null || assignment.status() == null) {
                throw invalid(field, "La asignación o el estado de competencia está incompleto");
            }
            requireId(assignment.competitionProblemId(), field + ".competitionProblemId");
            requireId(assignment.competitionId(), field + ".competitionId");
            requireId(assignment.problemId(), field + ".problemId");
            if (!teamId.equals(assignment.teamId())) {
                throw invalid(field + ".teamId", "La competencia no pertenece al equipo consultado");
            }
            TeamAssignedProblem previous = result.putIfAbsent(assignment.competitionProblemId(), assignment);
            CompetitionStatus status = statuses.putIfAbsent(assignment.competitionId(), assignment.status());
            if ((previous != null && !previous.equals(assignment)) || (status != null && status != assignment.status())) {
                throw invalid(field, "Existen datos contradictorios para la asignación o competencia");
            }
        }
        return result;
    }

    private Map<Integer, TopicTotals> validateTopics(Set<Integer> problems, List<ProblemTopicData> topics) {
        Map<Integer, TopicTotals> result = new HashMap<>();
        Set<Integer> classified = new HashSet<>();
        for (int index = 0; index < topics.size(); index++) {
            ProblemTopicData topic = topics.get(index);
            String field = "topics[" + index + "]";
            if (topic == null || topic.topicName() == null || topic.topicName().isBlank()) {
                throw invalid(field, "La clasificación por tema está incompleta");
            }
            requireId(topic.topicId(), field + ".topicId");
            if (!problems.contains(topic.problemId())) {
                throw invalid(field + ".problemId", "El problema no pertenece al universo analizado");
            }
            TopicTotals total = result.computeIfAbsent(topic.topicId(), id -> new TopicTotals(id, topic.topicName()));
            if (!total.name.equals(topic.topicName())) {
                throw invalid(field + ".topicName", "El mismo tema tiene nombres contradictorios");
            }
            total.problems.add(topic.problemId());
            classified.add(topic.problemId());
        }
        Set<Integer> missing = new java.util.TreeSet<>(problems);
        missing.removeAll(classified);
        if (!missing.isEmpty()) {
            throw invalid("topics", "Falta la clasificación por tema de los problemas: " + missing);
        }
        return result;
    }

    private ResolutionSummary consolidate(Integer teamId, Map<Integer, GroupMemberData> members,
                                          Map<Integer, TeamAssignedProblem> assignments,
                                          List<TeamResolutionData> resolutions) {
        ResolutionSummary result = new ResolutionSummary();
        Map<Integer, TeamResolutionData> seen = new HashMap<>();
        for (int index = 0; index < resolutions.size(); index++) {
            TeamResolutionData resolution = resolutions.get(index);
            String field = "resolutions[" + index + "]";
            validateResolution(teamId, members, assignments, resolution, field);
            TeamResolutionData previous = seen.putIfAbsent(resolution.resolutionId(), resolution);
            if (previous != null) {
                if (!previous.equals(resolution)) {
                    throw invalid(field + ".resolutionId", "Existen datos contradictorios para la misma resolución");
                }
                continue;
            }
            if (assignments.get(resolution.competitionProblemId()).status() != CompetitionStatus.FINALIZADA
                    || members.get(resolution.membershipId()).status() != MembershipStatus.ACTIVO) {
                continue;
            }
            if (resolution.verdict() == Verdict.PENDIENTE) {
                result.pendingCount++;
                result.pendingByProblem.merge(resolution.problemId(), 1, Integer::sum);
            } else {
                result.hasDefinitiveResolution = true;
                if (resolution.verdict() == Verdict.ACCEPTED) {
                    result.solversByProblem.computeIfAbsent(resolution.problemId(), id -> new HashSet<>())
                            .add(resolution.membershipId());
                }
            }
        }
        return result;
    }

    private void validateResolution(Integer teamId, Map<Integer, GroupMemberData> members,
                                    Map<Integer, TeamAssignedProblem> assignments,
                                    TeamResolutionData resolution, String field) {
        if (resolution == null) {
            throw invalid(field, "La resolución está incompleta");
        }
        requireId(resolution.resolutionId(), field + ".resolutionId");
        if (!teamId.equals(resolution.membershipTeamId()) || !teamId.equals(resolution.competitionTeamId())) {
            throw invalid(field + ".teamId", "La membresía y la competencia deben pertenecer al equipo consultado");
        }
        if (!members.containsKey(resolution.membershipId())) {
            throw invalid(field + ".membershipId", "La membresía de la resolución no está disponible");
        }
        TeamAssignedProblem assignment = assignments.get(resolution.competitionProblemId());
        if (assignment == null || !assignment.competitionId().equals(resolution.competitionId())
                || !assignment.problemId().equals(resolution.problemId())) {
            throw invalid(field + ".competitionProblemId", "La resolución no coincide con su competencia y problema");
        }
        if (resolution.verdict() == null) {
            throw invalid(field + ".verdict", "El veredicto de la resolución no está disponible");
        }
    }

    private TeamTopicReportResponse response(Integer teamId, int activeMembers, Map<Integer, TopicTotals> totals,
                                             ResolutionSummary summary) {
        for (TopicTotals total : totals.values()) {
            for (Integer problem : total.problems) {
                Set<Integer> solvers = summary.solversByProblem.getOrDefault(problem, Set.of());
                if (!solvers.isEmpty()) {
                    total.solved++;
                    total.solvers.addAll(solvers);
                }
                total.pending += summary.pendingByProblem.getOrDefault(problem, 0);
            }
        }
        List<TopicTotals> ordered = new ArrayList<>(totals.values());
        ordered.sort((left, right) -> {
            int comparison = compare(left, right);
            return comparison != 0 ? comparison : left.id.compareTo(right.id);
        });
        TopicTotals minimum = ordered.getFirst();
        List<TopicPerformance> topics = ordered.stream().map(t -> new TopicPerformance(t.id, t.name,
                t.problems.size(), t.solved, t.problems.size() - t.solved, t.solvers.size(), t.pending,
                BigDecimal.valueOf(t.solved).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(t.problems.size()), 2, RoundingMode.HALF_UP),
                compare(t, minimum) == 0)).toList();
        return new TeamTopicReportResponse(teamId, "DISTINCT_SOLVED_PROBLEMS_OVER_ASSIGNED_PROBLEMS",
                "EXACT_PROPORTION_MINIMUM_ALL_TIES",
                "Se comparan proporciones exactas de problemas distintos aceptados sobre asignados; todos los temas "
                        + "con la proporción mínima comparten menor cobertura. El porcentaje se redondea solo para mostrarlo.",
                activeMembers, summary.pendingCount, topics);
    }

    private int compare(TopicTotals left, TopicTotals right) {
        return Long.compare((long) left.solved * right.problems.size(), (long) right.solved * left.problems.size());
    }

    private void requireId(Integer id, String field) {
        if (id == null || id <= 0) {
            throw invalid(field, "El identificador debe ser un entero positivo");
        }
    }

    private void requireCollection(List<?> values, String field) {
        if (values == null) {
            throw invalid(field, "La información necesaria no está disponible");
        }
    }

    private TopicReportDataException invalid(String field, String message) {
        return new TopicReportDataException("No se pudo generar un reporte de temas válido", Map.of(field, message));
    }

    private static class TopicTotals {
        final Integer id;
        final String name;
        final Set<Integer> problems = new HashSet<>();
        final Set<Integer> solvers = new HashSet<>();
        int solved;
        int pending;

        TopicTotals(Integer id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private static class ResolutionSummary {
        final Map<Integer, Set<Integer>> solversByProblem = new HashMap<>();
        final Map<Integer, Integer> pendingByProblem = new HashMap<>();
        int pendingCount;
        boolean hasDefinitiveResolution;
    }
}

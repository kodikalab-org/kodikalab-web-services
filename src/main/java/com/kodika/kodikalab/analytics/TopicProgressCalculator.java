package com.kodika.kodikalab.analytics;

import com.kodika.kodikalab.analytics.dto.TopicProgressResponse;
import com.kodika.kodikalab.analytics.dto.TopicProgressResponse.TopicProgress;
import com.kodika.kodikalab.analytics.dto.TopicProgressResponse.TopicStatus;
import com.kodika.kodikalab.competitions.competitionproblem.dto.TeamAssignedProblem;
import com.kodika.kodikalab.competitions.problemresolution.Verdict;
import com.kodika.kodikalab.competitions.problemresolution.dto.MemberAttempt;
import com.kodika.kodikalab.problems.problemtopic.dto.ProblemTopicData;
import com.kodika.kodikalab.teams.groupmembership.MembershipStatus;
import com.kodika.kodikalab.teams.groupmembership.dto.GroupMemberData;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Progreso por tema de un practicante en un equipo (US-10). Es un cálculo puro: recibe los datos ya leídos y, si algo es
 * inconsistente, lanza {@link TopicProgressDataException} en lugar de mostrar indicadores incorrectos.
 *
 * <p>Un problema cuenta una sola vez por tema aunque se asigne en varias competencias; está resuelto si el practicante
 * tiene al menos un intento {@code ACCEPTED}. La cobertura de un tema es {@code 100 × resueltos / asignados}. Se marcan
 * para reforzar los temas con la menor cobertura (todos los empatados) mientras sea menor que 100%, con la misma
 * comparación exacta de proporciones que el reporte del coach (US-12).
 */
@Service
public class TopicProgressCalculator {
    static final String REINFORCEMENT_CRITERION = "Se marcan para reforzar los temas con la menor cobertura (todos los "
            + "empatados) mientras esa cobertura sea menor que 100%. La cobertura es la proporción de problemas "
            + "distintos resueltos sobre los asignados al equipo; el porcentaje se redondea solo para mostrarlo.";

    public TopicProgressResponse calculate(Integer teamId, GroupMemberData member, List<TeamAssignedProblem> assignments,
                                           List<MemberAttempt> attempts, List<ProblemTopicData> topics) {
        requireId(teamId, "teamId");
        requireMember(teamId, member);
        requireCollection(assignments, "assignments");
        requireCollection(attempts, "attempts");
        requireCollection(topics, "topics");
        Map<Integer, TeamAssignedProblem> assigned = validateAssignments(teamId, assignments);
        Set<Integer> problems = new HashSet<>();
        assigned.values().forEach(assignment -> problems.add(assignment.problemId()));
        Activity activity = consolidate(assigned, attempts);
        Classification classification = classify(problems, topics);
        return response(teamId, member, problems, activity, classification);
    }

    private void requireMember(Integer teamId, GroupMemberData member) {
        if (member == null) {
            throw invalid("member", "La membresía del practicante no está disponible");
        }
        requireId(member.membershipId(), "member.membershipId");
        requireId(member.userId(), "member.userId");
        if (!teamId.equals(member.teamId())) {
            throw invalid("member.teamId", "La membresía no pertenece al equipo consultado");
        }
        if (member.status() != MembershipStatus.ACTIVO) {
            throw invalid("member.status", "La membresía del practicante no está activa");
        }
    }

    private Map<Integer, TeamAssignedProblem> validateAssignments(Integer teamId, List<TeamAssignedProblem> assignments) {
        Map<Integer, TeamAssignedProblem> result = new HashMap<>();
        for (int index = 0; index < assignments.size(); index++) {
            TeamAssignedProblem assignment = assignments.get(index);
            String field = "assignments[" + index + "]";
            if (assignment == null) {
                throw invalid(field, "La asignación está incompleta");
            }
            requireId(assignment.competitionProblemId(), field + ".competitionProblemId");
            requireId(assignment.problemId(), field + ".problemId");
            if (!teamId.equals(assignment.teamId())) {
                throw invalid(field + ".teamId", "La asignación no pertenece al equipo consultado");
            }
            TeamAssignedProblem previous = result.putIfAbsent(assignment.competitionProblemId(), assignment);
            if (previous != null && !previous.equals(assignment)) {
                throw invalid(field, "Existen datos contradictorios para la asignación");
            }
        }
        return result;
    }

    /** Problemas con intentos, resueltos y por verificar del practicante; un intento repetido idéntico se ignora. */
    private Activity consolidate(Map<Integer, TeamAssignedProblem> assigned, List<MemberAttempt> attempts) {
        Activity activity = new Activity();
        Map<Integer, MemberAttempt> seen = new HashMap<>();
        for (int index = 0; index < attempts.size(); index++) {
            MemberAttempt attempt = attempts.get(index);
            String field = "attempts[" + index + "]";
            if (attempt == null) {
                throw invalid(field, "El intento está incompleto");
            }
            requireId(attempt.resolutionId(), field + ".resolutionId");
            requireId(attempt.competitionProblemId(), field + ".competitionProblemId");
            if (attempt.verdict() == null) {
                throw invalid(field + ".verdict", "El veredicto del intento no está disponible");
            }
            TeamAssignedProblem assignment = assigned.get(attempt.competitionProblemId());
            if (assignment == null) {
                throw invalid(field + ".competitionProblemId", "El intento no corresponde a un problema asignado al equipo");
            }
            MemberAttempt previous = seen.putIfAbsent(attempt.resolutionId(), attempt);
            if (previous != null) {
                if (!previous.equals(attempt)) {
                    throw invalid(field + ".resolutionId", "Existen datos contradictorios para el mismo intento");
                }
                continue;
            }
            activity.attempted.add(assignment.problemId());
            if (attempt.verdict() == Verdict.ACCEPTED) {
                activity.solved.add(assignment.problemId());
            } else if (attempt.verdict() == Verdict.PENDIENTE) {
                activity.pending.add(assignment.problemId());
            }
        }
        activity.pending.removeAll(activity.solved);
        return activity;
    }

    private Classification classify(Set<Integer> problems, List<ProblemTopicData> topics) {
        Classification result = new Classification();
        for (int index = 0; index < topics.size(); index++) {
            ProblemTopicData topic = topics.get(index);
            String field = "topics[" + index + "]";
            if (topic == null || topic.topicName() == null || topic.topicName().isBlank()) {
                throw invalid(field, "La clasificación por tema está incompleta");
            }
            requireId(topic.topicId(), field + ".topicId");
            if (topic.problemId() == null || !problems.contains(topic.problemId())) {
                throw invalid(field + ".problemId", "El problema no está asignado al equipo");
            }
            TopicTotals total = result.totals.computeIfAbsent(topic.topicId(),
                    id -> new TopicTotals(id, topic.topicName()));
            if (!total.name.equals(topic.topicName())) {
                throw invalid(field + ".topicName", "El mismo tema tiene nombres contradictorios");
            }
            total.problems.add(topic.problemId());
            result.classified.add(topic.problemId());
        }
        return result;
    }

    private TopicProgressResponse response(Integer teamId, GroupMemberData member, Set<Integer> problems,
                                           Activity activity, Classification classification) {
        List<TopicTotals> ordered = new ArrayList<>(classification.totals.values());
        for (TopicTotals total : ordered) {
            for (Integer problem : total.problems) {
                total.solved += activity.solved.contains(problem) ? 1 : 0;
                total.pending += activity.pending.contains(problem) ? 1 : 0;
                total.attempted += activity.attempted.contains(problem) ? 1 : 0;
            }
        }
        ordered.sort(Comparator.comparing((TopicTotals t) -> t, this::compare)
                .thenComparing(t -> t.name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(t -> t.id));
        TopicTotals minimum = ordered.isEmpty() ? null : ordered.getFirst();
        List<TopicProgress> rows = ordered.stream().map(t -> new TopicProgress(t.id, t.name, t.problems.size(),
                t.solved, t.problems.size() - t.solved, t.pending,
                BigDecimal.valueOf(t.solved).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(t.problems.size()), 2, RoundingMode.HALF_UP),
                status(t), compare(t, minimum) == 0 && minimum.solved < minimum.problems.size())).toList();
        return new TopicProgressResponse(teamId, member.membershipId(), member.userId(), problems.size(),
                activity.solved.size(), problems.size() - classification.classified.size(), REINFORCEMENT_CRITERION,
                rows);
    }

    private static TopicStatus status(TopicTotals total) {
        if (total.solved == total.problems.size()) {
            return TopicStatus.COMPLETADO;
        }
        return total.attempted == 0 ? TopicStatus.SIN_ACTIVIDAD : TopicStatus.EN_PROGRESO;
    }

    /** Compara las proporciones resueltos/asignados sin redondear: a/b < c/d si a·d < c·b. */
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

    private TopicProgressDataException invalid(String field, String message) {
        return new TopicProgressDataException("No se pudo calcular un progreso por tema válido", Map.of(field, message));
    }

    private static class TopicTotals {
        final Integer id;
        final String name;
        final Set<Integer> problems = new HashSet<>();
        int solved;
        int pending;
        int attempted;

        TopicTotals(Integer id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private static class Classification {
        final Map<Integer, TopicTotals> totals = new HashMap<>();
        final Set<Integer> classified = new HashSet<>();
    }

    private static class Activity {
        final Set<Integer> attempted = new HashSet<>();
        final Set<Integer> solved = new HashSet<>();
        final Set<Integer> pending = new HashSet<>();
    }
}

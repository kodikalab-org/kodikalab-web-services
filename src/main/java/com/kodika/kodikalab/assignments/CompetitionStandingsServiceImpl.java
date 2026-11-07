package com.kodika.kodikalab.assignments;

import com.kodika.kodikalab.assignments.dto.CompetitionStandingsResponse;
import com.kodika.kodikalab.assignments.dto.CompetitionStandingsResponse.Cell;
import com.kodika.kodikalab.assignments.dto.CompetitionStandingsResponse.ProblemInfo;
import com.kodika.kodikalab.assignments.dto.CompetitionStandingsResponse.Row;
import com.kodika.kodikalab.common.exception.ConflictException;
import com.kodika.kodikalab.common.exception.FieldValidationException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.competitions.competition.CompetitionService;
import com.kodika.kodikalab.competitions.competition.PenaltyRule;
import com.kodika.kodikalab.competitions.competition.dto.CompetitionDetail;
import com.kodika.kodikalab.competitions.competitionproblem.CompetitionProblemService;
import com.kodika.kodikalab.competitions.competitionproblem.dto.AssignmentData;
import com.kodika.kodikalab.competitions.problemresolution.ProblemResolutionService;
import com.kodika.kodikalab.competitions.problemresolution.Verdict;
import com.kodika.kodikalab.competitions.problemresolution.dto.CompetitionAttempt;
import com.kodika.kodikalab.problems.problem.ProblemService;
import com.kodika.kodikalab.problems.problem.dto.ProblemSummary;
import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.teams.groupmembership.GroupMembershipService;
import com.kodika.kodikalab.teams.groupmembership.MembershipStatus;
import com.kodika.kodikalab.teams.groupmembership.dto.GroupMemberData;
import com.kodika.kodikalab.teams.studygroup.StudyGroupService;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserStatus;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompetitionStandingsServiceImpl implements CompetitionStandingsService {
    /** Minutos que suma a la penalización cada respuesta fallida anterior a la aceptada (ICPC_20_MIN). */
    static final int WRONG_ATTEMPT_PENALTY_MINUTES = 20;

    private final CurrentUserResolver currentUserResolver;
    private final CompetitionService competitionService;
    private final StudyGroupService groupService;
    private final GroupMembershipService membershipService;
    private final CompetitionProblemService competitionProblemService;
    private final ProblemService problemService;
    private final ProblemResolutionService resolutionService;

    public CompetitionStandingsServiceImpl(CurrentUserResolver currentUserResolver,
                                           CompetitionService competitionService, StudyGroupService groupService,
                                           GroupMembershipService membershipService,
                                           CompetitionProblemService competitionProblemService,
                                           ProblemService problemService, ProblemResolutionService resolutionService) {
        this.currentUserResolver = currentUserResolver;
        this.competitionService = competitionService;
        this.groupService = groupService;
        this.membershipService = membershipService;
        this.competitionProblemService = competitionProblemService;
        this.problemService = problemService;
        this.resolutionService = resolutionService;
    }

    /** Fila aún sin posición: el resultado de un integrante en cada problema y sus totales. */
    private record Standing(GroupMemberData member, List<Cell> cells, int solved, int score, int penaltyMinutes) {
    }

    /** Celda con las respuestas fallidas que penalizan, que no se exponen en la respuesta. */
    private record Evaluated(Cell cell, int wrongBeforeAcceptance) {
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CompetitionStandingsResponse standings(Integer competitionId) {
        User user = activeUser();
        if (competitionId == null || competitionId <= 0) {
            throw new FieldValidationException("Los criterios de consulta deben corregirse",
                    Map.of("competitionId", "La competencia debe ser un entero positivo"));
        }
        CompetitionDetail competition = competitionService.findDetailById(competitionId)
                .orElseThrow(() -> new NotFoundException("La competencia no existe"));
        Integer teamId = competition.teamId();
        if (teamId == null) {
            throw new ConflictException("La información del equipo de la competencia está incompleta");
        }
        List<GroupMemberData> members = authorize(user, teamId);

        List<AssignmentData> assignments = competitionProblemService.findByCompetitionId(competitionId).stream()
                .sorted((left, right) -> AssignedProblemsServiceImpl.compareLetters(left.letter(), right.letter()))
                .toList();
        List<ProblemInfo> problems = problems(assignments);

        // Aislamiento del progreso: solo intentos de membresías activas de este equipo sobre problemas de esta
        // competencia, aunque la consulta ya filtre por equipo y competencia.
        Set<Integer> membershipIds = members.stream().map(GroupMemberData::membershipId).collect(Collectors.toSet());
        Set<Integer> assignmentIds = assignments.stream().map(AssignmentData::competitionProblemId)
                .collect(Collectors.toSet());
        Map<Integer, Map<Integer, List<CompetitionAttempt>>> attempts = new HashMap<>();
        resolutionService.findAttemptsByCompetition(competitionId, teamId).stream()
                .filter(attempt -> membershipIds.contains(attempt.membershipId())
                        && assignmentIds.contains(attempt.competitionProblemId()))
                .sorted(Comparator.comparing(CompetitionAttempt::submittedAt)
                        .thenComparing(CompetitionAttempt::resolutionId))
                .forEach(attempt -> attempts.computeIfAbsent(attempt.membershipId(), id -> new HashMap<>())
                        .computeIfAbsent(attempt.competitionProblemId(), id -> new ArrayList<>()).add(attempt));

        boolean icpc = competition.penaltyRule() != PenaltyRule.IOI_POINTS;
        List<Standing> standings = new ArrayList<>();
        for (GroupMemberData member : members) {
            standings.add(standing(member, assignments, attempts.getOrDefault(member.membershipId(), Map.of()),
                    competition.startsAt(), icpc));
        }
        return new CompetitionStandingsResponse(competition.id(), teamId, competition.eventName(),
                competition.status(), competition.penaltyRule(), competition.startsAt(), competition.endsAt(),
                problems, rank(standings, icpc));
    }

    private User activeUser() {
        User user = currentUserResolver.currentUser();
        if (user.getStatus() != UserStatus.ACTIVO) {
            throw new ForbiddenException("La cuenta no está activa");
        }
        return user;
    }

    /**
     * Verifica el acceso al equipo de la competencia: el coach responsable o un integrante con membresía ACTIVO.
     * Devuelve los integrantes ACTIVO, que son las filas de la clasificación.
     */
    private List<GroupMemberData> authorize(User user, Integer teamId) {
        var team = groupService.findSummaryById(teamId).orElseThrow(() -> new NotFoundException("El equipo no existe"));
        List<GroupMemberData> members = membershipService.findMembersByTeamId(teamId).stream()
                .filter(member -> member != null && member.status() == MembershipStatus.ACTIVO).toList();
        if (user.getRole() == Role.COACH) {
            if (!user.getId().equals(team.coachUserId())) {
                throw new ForbiddenException("No es el coach responsable de este equipo");
            }
        } else if (members.stream().noneMatch(member -> user.getId().equals(member.userId()))) {
            throw new ForbiddenException("No pertenece a este equipo");
        }
        return members;
    }

    private List<ProblemInfo> problems(List<AssignmentData> assignments) {
        if (assignments.isEmpty()) {
            return List.of();
        }
        Set<Integer> problemIds = assignments.stream().map(AssignmentData::problemId).collect(Collectors.toSet());
        Map<Integer, ProblemSummary> summaries = problemService.findSummariesByIds(problemIds).stream()
                .collect(Collectors.toMap(ProblemSummary::id, Function.identity()));
        List<ProblemInfo> problems = new ArrayList<>();
        for (AssignmentData assignment : assignments) {
            ProblemSummary summary = summaries.get(assignment.problemId());
            if (summary == null) {
                throw new IllegalStateException("La asignación " + assignment.competitionProblemId()
                        + " referencia un problema que no está en el catálogo");
            }
            problems.add(new ProblemInfo(assignment.competitionProblemId(), assignment.letter(), summary.title(),
                    assignment.score()));
        }
        return problems;
    }

    private static Standing standing(GroupMemberData member, List<AssignmentData> assignments,
                                     Map<Integer, List<CompetitionAttempt>> attemptsByAssignment,
                                     OffsetDateTime startsAt, boolean icpc) {
        List<Cell> cells = new ArrayList<>();
        int solved = 0;
        int score = 0;
        int penalty = 0;
        for (AssignmentData assignment : assignments) {
            Evaluated evaluated = evaluate(assignment, attemptsByAssignment.getOrDefault(
                    assignment.competitionProblemId(), List.of()), startsAt);
            cells.add(evaluated.cell());
            if (evaluated.cell().result() == AssignmentStatus.RESUELTO) {
                solved++;
                score += assignment.score() == null ? 0 : assignment.score();
                penalty += (evaluated.cell().minutes() == null ? 0 : evaluated.cell().minutes())
                        + WRONG_ATTEMPT_PENALTY_MINUTES * evaluated.wrongBeforeAcceptance();
            }
        }
        return new Standing(member, cells, solved, score, icpc ? penalty : 0);
    }

    /** Intentos del más antiguo al más reciente; el estado se deriva igual que en los problemas asignados. */
    private static Evaluated evaluate(AssignmentData assignment, List<CompetitionAttempt> attempts,
                                      OffsetDateTime startsAt) {
        AssignmentStatus result = AssignedProblemsServiceImpl.statusOfVerdicts(
                attempts.stream().map(CompetitionAttempt::verdict).toList());
        int accepted = -1;
        for (int index = 0; index < attempts.size() && accepted < 0; index++) {
            if (attempts.get(index).verdict() == Verdict.ACCEPTED) {
                accepted = index;
            }
        }
        if (accepted < 0) {
            return new Evaluated(new Cell(assignment.competitionProblemId(), assignment.letter(), result,
                    attempts.size(), null), 0);
        }
        // Una respuesta pendiente de verificar no penaliza: solo las ya juzgadas como fallidas.
        int wrong = (int) attempts.subList(0, accepted).stream()
                .filter(attempt -> attempt.verdict() != Verdict.PENDIENTE).count();
        Integer minutes = startsAt == null || attempts.get(accepted).submittedAt() == null ? null
                : (int) Math.max(0, Duration.between(startsAt, attempts.get(accepted).submittedAt()).toMinutes());
        return new Evaluated(new Cell(assignment.competitionProblemId(), assignment.letter(), result, accepted + 1,
                minutes), wrong);
    }

    /** Ordena por el criterio de la regla y asigna la posición; los empates la comparten (1, 1, 3). */
    private static List<Row> rank(List<Standing> standings, boolean icpc) {
        Comparator<Standing> criterion = icpc
                ? Comparator.comparingInt(Standing::solved).reversed().thenComparingInt(Standing::penaltyMinutes)
                : Comparator.comparingInt(Standing::score).reversed();
        List<Standing> sorted = new ArrayList<>(standings);
        sorted.sort(criterion
                .thenComparing(standing -> standing.member().fullName(),
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(standing -> standing.member().userId()));

        List<Row> rows = new ArrayList<>();
        int position = 0;
        for (int index = 0; index < sorted.size(); index++) {
            Standing current = sorted.get(index);
            if (index == 0 || criterion.compare(sorted.get(index - 1), current) != 0) {
                position = index + 1;
            }
            rows.add(new Row(position, current.member().userId(), current.member().fullName(), current.solved(),
                    current.score(), current.penaltyMinutes(), current.cells()));
        }
        return rows;
    }
}

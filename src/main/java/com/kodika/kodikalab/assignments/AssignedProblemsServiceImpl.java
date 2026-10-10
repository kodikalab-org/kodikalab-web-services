package com.kodika.kodikalab.assignments;

import com.kodika.kodikalab.assignments.dto.AssignedProblemDetailResponse;
import com.kodika.kodikalab.assignments.dto.AssignedProblemResponse;
import com.kodika.kodikalab.assignments.dto.AssignedProblemResponse.AttemptInfo;
import com.kodika.kodikalab.assignments.dto.AssignedProblemResponse.CompetitionInfo;
import com.kodika.kodikalab.assignments.dto.AssignedProblemResponse.ProblemInfo;
import com.kodika.kodikalab.assignments.dto.AssignedProblemsQuery;
import com.kodika.kodikalab.assignments.dto.AssignedProblemsResponse;
import com.kodika.kodikalab.common.exception.FieldValidationException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.competitions.competitionproblem.CompetitionProblemService;
import com.kodika.kodikalab.competitions.competitionproblem.dto.AssignmentView;
import com.kodika.kodikalab.competitions.problemresolution.ProblemResolutionService;
import com.kodika.kodikalab.competitions.problemresolution.Verdict;
import com.kodika.kodikalab.competitions.problemresolution.dto.MemberAttempt;
import com.kodika.kodikalab.problems.problem.ProblemService;
import com.kodika.kodikalab.problems.problem.dto.ProblemSummary;
import com.kodika.kodikalab.problems.problemtopic.ProblemTopicService;
import com.kodika.kodikalab.problems.problemtopic.dto.ProblemTopicData;
import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.teams.groupmembership.GroupMembershipService;
import com.kodika.kodikalab.teams.groupmembership.MembershipStatus;
import com.kodika.kodikalab.teams.studygroup.StudyGroupService;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssignedProblemsServiceImpl implements AssignedProblemsService {
    static final int MAX_QUERY = 100;
    static final int MAX_DIFFICULTY = 30;
    static final Set<String> SORTS = Set.of("letter", "title", "difficulty", "assignedAt", "status");
    private static final String VALIDATION_MESSAGE = "Los criterios de consulta deben corregirse";

    private final CurrentUserResolver currentUserResolver;
    private final StudyGroupService groupService;
    private final GroupMembershipService membershipService;
    private final CompetitionProblemService competitionProblemService;
    private final ProblemService problemService;
    private final ProblemTopicService problemTopicService;
    private final ProblemResolutionService resolutionService;

    public AssignedProblemsServiceImpl(CurrentUserResolver currentUserResolver, StudyGroupService groupService,
                                       GroupMembershipService membershipService,
                                       CompetitionProblemService competitionProblemService,
                                       ProblemService problemService, ProblemTopicService problemTopicService,
                                       ProblemResolutionService resolutionService) {
        this.currentUserResolver = currentUserResolver;
        this.groupService = groupService;
        this.membershipService = membershipService;
        this.competitionProblemService = competitionProblemService;
        this.problemService = problemService;
        this.problemTopicService = problemTopicService;
        this.resolutionService = resolutionService;
    }

    /** Quién consulta: un practicante (con su membresía) o el coach responsable ({@code membershipId == null}). */
    private record Access(Integer membershipId) {
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AssignedProblemsResponse list(AssignedProblemsQuery query) {
        User user = activeUser();
        validate(query, user);
        Access access = authorize(user, query.teamId());

        List<AssignmentView> views = competitionProblemService.findViewsByTeamId(query.teamId());
        List<AssignedProblemResponse> items = build(views, access, attemptsOf(access));
        items = filter(items, query);
        items = sort(items, query);
        return new AssignedProblemsResponse(query.teamId(), items.size(), items);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AssignedProblemDetailResponse detail(Integer competitionProblemId) {
        User user = activeUser();
        if (competitionProblemId == null || competitionProblemId <= 0) {
            throw new FieldValidationException(VALIDATION_MESSAGE, Map.of("competitionProblemId",
                    "La asignación debe ser un entero positivo"));
        }
        AssignmentView view = competitionProblemService.findViewById(competitionProblemId)
                .orElseThrow(() -> new NotFoundException("La asignación no existe"));
        Access access = authorize(user, view.teamId());

        Map<Integer, List<MemberAttempt>> attemptsByAssignment = attemptsOf(access);
        AssignedProblemResponse assignment = build(List.of(view), access, attemptsByAssignment).get(0);
        List<AttemptInfo> attempts = attemptsByAssignment.getOrDefault(view.competitionProblemId(), List.of()).stream()
                .map(AssignedProblemsServiceImpl::attemptInfo).toList();
        return new AssignedProblemDetailResponse(assignment, attempts);
    }

    private User activeUser() {
        User user = currentUserResolver.currentUser();
        if (user.getStatus() != UserStatus.ACTIVO) {
            throw new ForbiddenException("La cuenta no está activa");
        }
        return user;
    }

    private static void validate(AssignedProblemsQuery query, User user) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (query.teamId() == null || query.teamId() <= 0) {
            errors.put("teamId", "El equipo es obligatorio y debe ser un entero positivo");
        }
        if (query.competitionId() != null && query.competitionId() <= 0) {
            errors.put("competitionId", "La competencia debe ser un entero positivo");
        }
        if (query.status() != null && user.getRole() != Role.PRACTICANTE) {
            errors.put("status", "El estado personal solo está disponible para practicantes");
        }
        if (query.query() != null && query.query().strip().length() > MAX_QUERY) {
            errors.put("q", "La búsqueda debe tener como máximo " + MAX_QUERY + " caracteres");
        }
        if (query.difficulty() != null && query.difficulty().strip().length() > MAX_DIFFICULTY) {
            errors.put("difficulty", "La dificultad debe tener como máximo " + MAX_DIFFICULTY + " caracteres");
        }
        if (query.sort() != null && !SORTS.contains(query.sort())) {
            errors.put("sort", "Valores permitidos: " + String.join(", ", SORTS.stream().sorted().toList()));
        }
        if (query.order() != null && !query.order().equals("asc") && !query.order().equals("desc")) {
            errors.put("order", "Valores permitidos: asc, desc");
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(VALIDATION_MESSAGE, errors);
        }
    }

    /** Verifica el acceso al equipo sin revelar datos: un practicante necesita membresía ACTIVO; un coach, ser el responsable. */
    private Access authorize(User user, Integer teamId) {
        var team = groupService.findSummaryById(teamId).orElseThrow(() -> new NotFoundException("El equipo no existe"));
        if (user.getRole() == Role.COACH) {
            if (!user.getId().equals(team.coachUserId())) {
                throw new ForbiddenException("No es el coach responsable de este equipo");
            }
            return new Access(null);
        }
        Optional<Integer> membershipId = membershipService.findMembersByTeamId(teamId).stream()
                .filter(member -> member != null && user.getId().equals(member.userId())
                        && member.status() == MembershipStatus.ACTIVO)
                .map(member -> member.membershipId()).findFirst();
        return new Access(membershipId.orElseThrow(() -> new ForbiddenException("No pertenece a este equipo")));
    }

    private List<AssignedProblemResponse> build(List<AssignmentView> views, Access access,
                                                 Map<Integer, List<MemberAttempt>> attempts) {
        if (views.isEmpty()) {
            return List.of();
        }
        Set<Integer> problemIds = views.stream().map(AssignmentView::problemId).collect(Collectors.toSet());
        Map<Integer, ProblemSummary> problems = problemService.findSummariesByIds(problemIds).stream()
                .collect(Collectors.toMap(ProblemSummary::id, summary -> summary));
        Map<Integer, List<String>> topics = new HashMap<>();
        for (ProblemTopicData topic : problemTopicService.findTopicsByProblemIds(problemIds)) {
            topics.computeIfAbsent(topic.problemId(), id -> new ArrayList<>()).add(topic.topicName());
        }

        List<AssignedProblemResponse> result = new ArrayList<>();
        for (AssignmentView view : views) {
            ProblemSummary problem = problems.get(view.problemId());
            if (problem == null) {
                throw new IllegalStateException("La asignación " + view.competitionProblemId()
                        + " referencia un problema que no está en el catálogo");
            }
            result.add(response(view, problem, topics.getOrDefault(view.problemId(), List.of()), access,
                    attempts.getOrDefault(view.competitionProblemId(), List.of())));
        }
        return result;
    }

    /** Intentos del practicante agrupados por asignación, el más reciente primero; vacío para el coach. */
    private Map<Integer, List<MemberAttempt>> attemptsOf(Access access) {
        if (access.membershipId() == null) {
            return Map.of();
        }
        return resolutionService.findAttemptsByMembershipId(access.membershipId()).stream()
                .collect(Collectors.groupingBy(MemberAttempt::competitionProblemId));
    }

    private static AssignedProblemResponse response(AssignmentView view, ProblemSummary problem, List<String> topics,
                                                    Access access, List<MemberAttempt> attempts) {
        CompetitionInfo competition = new CompetitionInfo(view.competitionId(), view.competitionName(),
                view.competitionStatus(), view.startsAt(), view.endsAt(), view.durationMinutes(), view.penaltyRule(),
                view.scoreboardFreezeMinutes(), view.accessType());
        ProblemInfo problemInfo = new ProblemInfo(problem.id(), problem.title(), problem.url(),
                problem.sourcePlatform(), problem.sourceCode(), problem.difficultyRating(), problem.timeLimitMs(),
                problem.memoryLimitMb(), topics);
        boolean personal = access.membershipId() != null;
        return new AssignedProblemResponse(view.competitionProblemId(), view.letter(), view.score(),
                view.balloonColor(), view.assignedAt(), competition, problemInfo,
                personal ? statusOf(attempts) : null, personal ? attempts.size() : null,
                personal && !attempts.isEmpty() ? attemptInfo(attempts.get(0)) : null);
    }

    static AssignmentStatus statusOf(List<MemberAttempt> attempts) {
        boolean pending = false;
        for (MemberAttempt attempt : attempts) {
            if (attempt.verdict() == Verdict.ACCEPTED) {
                return AssignmentStatus.RESUELTO;
            }
            pending |= attempt.verdict() == Verdict.PENDIENTE;
        }
        if (attempts.isEmpty()) {
            return AssignmentStatus.SIN_INTENTOS;
        }
        return pending ? AssignmentStatus.PENDIENTE : AssignmentStatus.EN_PROGRESO;
    }

    private static AttemptInfo attemptInfo(MemberAttempt attempt) {
        return new AttemptInfo(attempt.resolutionId(), attempt.verdict(), attempt.language(), attempt.submittedAt(),
                attempt.evidenceUrl());
    }

    private static List<AssignedProblemResponse> filter(List<AssignedProblemResponse> items,
                                                         AssignedProblemsQuery query) {
        String text = query.query() == null || query.query().isBlank() ? null
                : query.query().strip().toLowerCase(Locale.ROOT);
        String difficulty = query.difficulty() == null || query.difficulty().isBlank() ? null
                : query.difficulty().strip();
        return items.stream()
                .filter(item -> query.competitionId() == null || query.competitionId().equals(item.competition().id()))
                .filter(item -> query.competitionStatus() == null
                        || query.competitionStatus() == item.competition().status())
                .filter(item -> query.status() == null || query.status() == item.status())
                .filter(item -> difficulty == null || difficulty.equalsIgnoreCase(item.problem().difficultyRating()))
                .filter(item -> text == null || contains(item.problem().title(), text)
                        || contains(item.problem().sourceCode(), text))
                .toList();
    }

    private static boolean contains(String value, String lowerText) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowerText);
    }

    /** Ordena por el criterio pedido; los empates conservan el orden por defecto (el de entrada). */
    private static List<AssignedProblemResponse> sort(List<AssignedProblemResponse> items,
                                                       AssignedProblemsQuery query) {
        if (query.sort() == null) {
            return items;
        }
        boolean descending = "desc".equals(query.order());
        Comparator<AssignedProblemResponse> comparator = switch (query.sort()) {
            case "letter" -> by(AssignedProblemResponse::letter, AssignedProblemsServiceImpl::compareLetters, descending);
            case "title" -> by(item -> item.problem().title(), String.CASE_INSENSITIVE_ORDER, descending);
            case "assignedAt" -> by(AssignedProblemResponse::assignedAt, Comparator.naturalOrder(), descending);
            case "status" -> by(AssignedProblemResponse::status, Comparator.naturalOrder(), descending);
            default -> by(item -> item.problem().difficultyRating(), AssignedProblemsServiceImpl::compareDifficulty,
                    descending);
        };
        List<AssignedProblemResponse> sorted = new ArrayList<>(items);
        sorted.sort(comparator); // estable: los empates mantienen el orden de entrada
        return sorted;
    }

    /** Compara por una clave, ascendente o descendente, dejando siempre al final las asignaciones sin ella. */
    private static <K> Comparator<AssignedProblemResponse> by(Function<AssignedProblemResponse, K> key,
                                                              Comparator<K> order, boolean descending) {
        return Comparator.comparing(key, Comparator.nullsLast(descending ? order.reversed() : order));
    }

    /** A < B < ... < Z < AA < AB: primero por longitud y luego alfabéticamente. */
    static int compareLetters(String left, String right) {
        return left.length() != right.length() ? Integer.compare(left.length(), right.length()) : left.compareTo(right);
    }

    /** Orden total: las dificultades numéricas primero (800 < 1400), luego las textuales alfabéticamente. */
    static int compareDifficulty(String left, String right) {
        Integer leftNumber = number(left);
        Integer rightNumber = number(right);
        if (leftNumber != null && rightNumber != null) {
            return Integer.compare(leftNumber, rightNumber);
        }
        if (leftNumber != null || rightNumber != null) {
            return leftNumber != null ? -1 : 1;
        }
        return left.compareToIgnoreCase(right);
    }

    private static Integer number(String value) {
        try {
            return Integer.valueOf(value.strip());
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}

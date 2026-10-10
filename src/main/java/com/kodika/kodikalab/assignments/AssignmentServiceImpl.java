package com.kodika.kodikalab.assignments;

import com.kodika.kodikalab.assignments.dto.AssignProblemsRequest;
import com.kodika.kodikalab.assignments.dto.AssignProblemsRequest.Item;
import com.kodika.kodikalab.assignments.dto.AssignProblemsResponse;
import com.kodika.kodikalab.assignments.dto.AssignProblemsResponse.AssignedItem;
import com.kodika.kodikalab.common.exception.ConflictException;
import com.kodika.kodikalab.common.exception.FieldConflictException;
import com.kodika.kodikalab.common.exception.FieldValidationException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.competitions.competition.CompetitionService;
import com.kodika.kodikalab.competitions.competition.CompetitionStatus;
import com.kodika.kodikalab.competitions.competition.dto.CompetitionSummary;
import com.kodika.kodikalab.competitions.competitionproblem.CompetitionProblemService;
import com.kodika.kodikalab.competitions.competitionproblem.dto.AssignmentData;
import com.kodika.kodikalab.competitions.competitionproblem.dto.NewAssignment;
import com.kodika.kodikalab.problems.problem.ProblemService;
import com.kodika.kodikalab.problems.problem.dto.ProblemSummary;
import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.teams.studygroup.StudyGroupService;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssignmentServiceImpl implements AssignmentService {
    static final int MAX_PROBLEMS_PER_REQUEST = 50;
    static final int DEFAULT_SCORE = 1;
    static final String DEFAULT_BALLOON_COLOR = "#FF0000";
    private static final int MAX_LETTER_LENGTH = 5;
    private static final Pattern LETTER = Pattern.compile("[A-Za-z]{1,5}");
    private static final Pattern COLOR = Pattern.compile("#[0-9A-Fa-f]{6}");
    private static final String VALIDATION_MESSAGE = "Los datos de la asignación deben corregirse";

    private final CurrentUserResolver currentUserResolver;
    private final StudyGroupService groupService;
    private final CompetitionService competitionService;
    private final CompetitionProblemService competitionProblemService;
    private final ProblemService problemService;

    public AssignmentServiceImpl(CurrentUserResolver currentUserResolver, StudyGroupService groupService,
                                 CompetitionService competitionService,
                                 CompetitionProblemService competitionProblemService, ProblemService problemService) {
        this.currentUserResolver = currentUserResolver;
        this.groupService = groupService;
        this.competitionService = competitionService;
        this.competitionProblemService = competitionProblemService;
        this.problemService = problemService;
    }

    @Override
    @Transactional
    public AssignProblemsResponse assign(AssignProblemsRequest request) {
        User coach = activeCoach();
        if (request == null) {
            throw new FieldValidationException(VALIDATION_MESSAGE, Map.of("body", "Debe enviar los datos de la asignación"));
        }
        Integer competitionId = request.competitionId();
        if (competitionId == null || competitionId <= 0) {
            throw new FieldValidationException(VALIDATION_MESSAGE,
                    Map.of("competitionId", "La competencia es obligatoria y debe ser un entero positivo"));
        }
        CompetitionSummary competition = competitionService.findSummaryForUpdate(competitionId)
                .orElseThrow(() -> new NotFoundException("La competencia no existe"));
        var team = competition.teamId() == null ? null : groupService.findSummaryById(competition.teamId()).orElse(null);
        if (team == null || team.coachUserId() == null) {
            throw new ConflictException("La información del equipo o de su coach responsable está incompleta");
        }
        if (!team.coachUserId().equals(coach.getId())) {
            throw new ForbiddenException("No tiene autorización para asignar problemas en esta competencia");
        }

        List<Item> items = request.problems() == null ? List.of() : request.problems();
        List<Normalized> normalized = normalize(items);
        if (competition.status() == CompetitionStatus.FINALIZADA) {
            throw new ConflictException("La competencia ya finalizó y no admite nuevos problemas");
        }

        Map<Integer, ProblemSummary> catalog = new HashMap<>();
        problemService.findSummariesByIds(normalized.stream().map(Normalized::problemId).toList())
                .forEach(summary -> catalog.put(summary.id(), summary));
        Map<String, String> missing = new LinkedHashMap<>();
        for (int index = 0; index < normalized.size(); index++) {
            if (!catalog.containsKey(normalized.get(index).problemId())) {
                missing.put(path(index, "problemId"), "El problema no existe en el catálogo");
            }
        }
        if (!missing.isEmpty()) {
            throw new FieldValidationException(VALIDATION_MESSAGE, missing);
        }

        List<AssignmentData> existing = competitionProblemService.findByCompetitionId(competitionId);
        Set<Integer> assignedProblems = new HashSet<>();
        Set<String> takenLetters = new HashSet<>();
        existing.forEach(row -> {
            assignedProblems.add(row.problemId());
            takenLetters.add(row.letter().toUpperCase(Locale.ROOT));
        });
        Map<String, String> conflicts = new LinkedHashMap<>();
        for (int index = 0; index < normalized.size(); index++) {
            Normalized item = normalized.get(index);
            if (assignedProblems.contains(item.problemId())) {
                conflicts.put(path(index, "problemId"), "El problema ya está asignado a la competencia");
            }
            if (item.letter() != null && takenLetters.contains(item.letter())) {
                conflicts.put(path(index, "letter"), "La letra ya está en uso en la competencia");
            }
        }
        if (!conflicts.isEmpty()) {
            throw new FieldConflictException("No se registró ninguna asignación; las existentes se mantienen", conflicts);
        }

        normalized.stream().map(Normalized::letter).filter(letter -> letter != null).forEach(takenLetters::add);
        List<NewAssignment> assignments = new ArrayList<>();
        for (Normalized item : normalized) {
            String letter = item.letter() != null ? item.letter() : nextFreeLetter(takenLetters);
            takenLetters.add(letter);
            assignments.add(new NewAssignment(item.problemId(), letter, item.score(), item.balloonColor()));
        }

        List<AssignedItem> assigned = competitionProblemService.assign(competitionId, assignments).stream()
                .map(row -> new AssignedItem(row.competitionProblemId(), row.problemId(),
                        catalog.get(row.problemId()).title(), row.letter(), row.score(), row.balloonColor(),
                        row.assignedAt()))
                .toList();
        return new AssignProblemsResponse(competitionId, competition.teamId(), assigned);
    }

    private record Normalized(Integer problemId, String letter, Integer score, String balloonColor) {
    }

    /** Valida y normaliza los problemas de la solicitud; informa todos los campos inválidos juntos. */
    private static List<Normalized> normalize(List<Item> items) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (items.isEmpty()) {
            errors.put("problems", "Debe incluir al menos un problema");
        } else if (items.size() > MAX_PROBLEMS_PER_REQUEST) {
            errors.put("problems", "Se admiten como máximo " + MAX_PROBLEMS_PER_REQUEST + " problemas por solicitud");
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(VALIDATION_MESSAGE, errors);
        }
        List<Normalized> result = new ArrayList<>();
        Set<Integer> problemIds = new HashSet<>();
        Set<String> letters = new HashSet<>();
        for (int index = 0; index < items.size(); index++) {
            Item item = items.get(index);
            Integer problemId = item.problemId();
            if (problemId == null || problemId <= 0) {
                errors.put(path(index, "problemId"), "El problema es obligatorio y debe ser un entero positivo");
            } else if (!problemIds.add(problemId)) {
                errors.put(path(index, "problemId"), "El problema está repetido en la solicitud");
            }
            String letter = null;
            if (item.letter() != null) {
                String candidate = item.letter().strip();
                if (!LETTER.matcher(candidate).matches()) {
                    errors.put(path(index, "letter"),
                            "La letra debe tener entre 1 y " + MAX_LETTER_LENGTH + " letras de la A a la Z");
                } else {
                    letter = candidate.toUpperCase(Locale.ROOT);
                    if (!letters.add(letter)) {
                        errors.put(path(index, "letter"), "La letra está repetida en la solicitud");
                    }
                }
            }
            int score = item.score() == null ? DEFAULT_SCORE : item.score();
            if (score < 1) {
                errors.put(path(index, "score"), "El puntaje debe ser de al menos 1");
            }
            String color = DEFAULT_BALLOON_COLOR;
            if (item.balloonColor() != null) {
                String candidate = item.balloonColor().strip();
                if (!COLOR.matcher(candidate).matches()) {
                    errors.put(path(index, "balloonColor"), "El color debe tener el formato #RRGGBB");
                } else {
                    color = candidate.toUpperCase(Locale.ROOT);
                }
            }
            result.add(new Normalized(problemId, letter, score, color));
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(VALIDATION_MESSAGE, errors);
        }
        return result;
    }

    /** Primera letra libre en la secuencia A, B, ... Z, AA, AB, ...; ocupa su lugar en {@code taken}. */
    static String nextFreeLetter(Set<String> taken) {
        for (int number = 1; ; number++) {
            String label = letterLabel(number);
            if (label.length() > MAX_LETTER_LENGTH) {
                throw new ConflictException("La competencia no tiene letras libres");
            }
            if (!taken.contains(label)) {
                return label;
            }
        }
    }

    static String letterLabel(int number) {
        StringBuilder label = new StringBuilder();
        for (int remaining = number; remaining > 0; remaining = (remaining - 1) / 26) {
            label.insert(0, (char) ('A' + (remaining - 1) % 26));
        }
        return label.toString();
    }

    private static String path(int index, String field) {
        return "problems[" + index + "]." + field;
    }

    private User activeCoach() {
        User coach = currentUserResolver.currentUser();
        if (coach.getStatus() != UserStatus.ACTIVO || coach.getRole() != Role.COACH) {
            throw new ForbiddenException("Solo un coach activo puede asignar problemas");
        }
        return coach;
    }
}

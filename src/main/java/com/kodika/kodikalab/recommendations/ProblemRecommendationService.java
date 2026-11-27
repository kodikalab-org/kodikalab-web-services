package com.kodika.kodikalab.recommendations;

import com.kodika.kodikalab.analytics.TeamTopicReportService;
import com.kodika.kodikalab.analytics.dto.TeamTopicReportResponse;
import com.kodika.kodikalab.analytics.dto.TeamTopicReportResponse.TopicPerformance;
import com.kodika.kodikalab.common.exception.BadRequestException;
import com.kodika.kodikalab.competitions.competitionproblem.CompetitionProblemService;
import com.kodika.kodikalab.competitions.competitionproblem.dto.TeamAssignedProblem;
import com.kodika.kodikalab.competitions.problemresolution.ProblemResolutionService;
import com.kodika.kodikalab.competitions.problemresolution.Verdict;
import com.kodika.kodikalab.competitions.problemresolution.dto.TeamResolutionData;
import com.kodika.kodikalab.problems.codeforces.CodeforcesProblemCatalog;
import com.kodika.kodikalab.problems.problem.ProblemService;
import com.kodika.kodikalab.problems.problem.SourcePlatform;
import com.kodika.kodikalab.problems.problem.dto.ProblemPageResponse;
import com.kodika.kodikalab.problems.problem.dto.ProblemResponse;
import com.kodika.kodikalab.problems.problem.dto.ProblemSearch;
import com.kodika.kodikalab.problems.problem.dto.ProblemSummary;
import com.kodika.kodikalab.profiles.practitioner.integration.CodeforcesProblem;
import com.kodika.kodikalab.profiles.practitioner.integration.CodeforcesUnavailableException;
import com.kodika.kodikalab.recommendations.ProblemRecommendation.Source;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * US-16: propone problemas para los temas con menor cobertura de un equipo. El permiso y la suficiencia de datos los
 * comprueba {@link TeamTopicReportService}, que se consulta primero.
 * <p>
 * No es transaccional a propósito: no debe retener una conexión mientras se descarga el problemset de Codeforces.
 */
@Service
public class ProblemRecommendationService {
    static final int MAX_TOPICS = 5;
    static final int PER_TOPIC = 3;
    /** Cuánto por encima de la dificultad típica del equipo se sigue considerando compatible. */
    static final int STRETCH = 300;
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 10;
    /** Candidatos de Codeforces que se contrastan con el catálogo por tema. */
    private static final int CODEFORCES_CANDIDATES = 100;
    private static final int MAX_RATING = 10_000;

    private final TeamTopicReportService reportService;
    private final CompetitionProblemService assignmentService;
    private final ProblemResolutionService resolutionService;
    private final ProblemService problemService;
    private final CodeforcesProblemCatalog codeforcesCatalog;

    public ProblemRecommendationService(TeamTopicReportService reportService,
                                        CompetitionProblemService assignmentService,
                                        ProblemResolutionService resolutionService, ProblemService problemService,
                                        CodeforcesProblemCatalog codeforcesCatalog) {
        this.reportService = reportService;
        this.assignmentService = assignmentService;
        this.resolutionService = resolutionService;
        this.problemService = problemService;
        this.codeforcesCatalog = codeforcesCatalog;
    }

    public RecommendationResult recommend(Integer teamId, RecommendationCriteria criteria) {
        TeamTopicReportResponse report = reportService.getReport(teamId);
        validate(criteria);
        List<String> notices = new ArrayList<>();
        List<TopicPerformance> topics = priorities(report, criteria.topic(), notices);
        if (topics.isEmpty()) {
            return new RecommendationResult(report, List.of(), null, null, notices);
        }
        Range range = range(teamId, criteria);
        Set<Integer> assigned = assignmentService.findAssignedProblemsByTeamId(teamId).stream()
                .map(TeamAssignedProblem::problemId).collect(Collectors.toSet());
        Codeforces codeforces = new Codeforces();
        Set<String> proposed = new HashSet<>();
        List<ProblemRecommendation> result = new ArrayList<>();
        List<String> withoutOptions = new ArrayList<>();
        for (TopicPerformance topic : topics) {
            List<ProblemRecommendation> options = new ArrayList<>(fromCatalog(topic, range, assigned, proposed));
            if (options.size() < PER_TOPIC) {
                options.addAll(codeforces.options(topic, range, PER_TOPIC - options.size(), proposed));
            }
            if (options.isEmpty()) {
                withoutOptions.add(topic.topicName());
            }
            options.sort(Comparator.comparing((ProblemRecommendation p) -> rating(p.difficulty()),
                    Comparator.nullsLast(Comparator.naturalOrder())));
            result.addAll(options);
        }
        if (codeforces.failed) {
            notices.add("No se pudo consultar Codeforces; solo se muestran opciones del catálogo");
        }
        if (!withoutOptions.isEmpty()) {
            notices.add("No hay problemas adecuados sin asignar para: " + String.join(", ", withoutOptions)
                    + range.describe());
        }
        return new RecommendationResult(report, result, range.min(), range.max(), notices);
    }

    private static void validate(RecommendationCriteria criteria) {
        Integer min = criteria.minDifficulty();
        Integer max = criteria.maxDifficulty();
        if ((min != null && (min < 0 || min > MAX_RATING)) || (max != null && (max < 0 || max > MAX_RATING))
                || (min != null && max != null && min > max)) {
            throw new BadRequestException("El rango de dificultad no es válido");
        }
    }

    private static List<TopicPerformance> priorities(TeamTopicReportResponse report, String wanted,
                                                     List<String> notices) {
        if (wanted == null || wanted.isBlank()) {
            return report.topics().stream().filter(TopicPerformance::lowestCoverage).limit(MAX_TOPICS).toList();
        }
        List<TopicPerformance> named = report.topics().stream()
                .filter(topic -> fold(topic.topicName()).equals(fold(wanted))).limit(1).toList();
        if (named.isEmpty()) {
            notices.add("El equipo no tiene actividad en el tema \"" + wanted.strip() + "\". Temas disponibles: "
                    + report.topics().stream().map(TopicPerformance::topicName).collect(Collectors.joining(", ")));
        }
        return named;
    }

    /** A requested range wins; otherwise it starts at the median rating of what the team solved. */
    private Range range(Integer teamId, RecommendationCriteria criteria) {
        if (criteria.minDifficulty() != null || criteria.maxDifficulty() != null) {
            return new Range(criteria.minDifficulty(), criteria.maxDifficulty());
        }
        Set<Integer> solved = resolutionService.findResolutionsByTeamId(teamId).stream()
                .filter(resolution -> resolution.verdict() == Verdict.ACCEPTED)
                .map(TeamResolutionData::problemId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<Integer> ratings = solved.isEmpty() ? List.of() : problemService.findSummariesByIds(solved).stream()
                .map(ProblemSummary::difficultyRating).map(ProblemRecommendationService::rating)
                .filter(Objects::nonNull).sorted().toList();
        if (ratings.isEmpty()) {
            return new Range(null, null);
        }
        int middle = ratings.size() / 2;
        int median = ratings.size() % 2 == 1 ? ratings.get(middle)
                : (ratings.get(middle - 1) + ratings.get(middle)) / 2;
        return new Range(median, median + STRETCH);
    }

    private List<ProblemRecommendation> fromCatalog(TopicPerformance topic, Range range, Set<Integer> assigned,
                                                    Set<String> proposed) {
        List<ProblemResponse> candidates = new ArrayList<>();
        ProblemPageResponse page;
        int number = 0;
        do {
            page = problemService.search(new ProblemSearch(null, topic.topicId(), null, null, number, PAGE_SIZE));
            page.items().stream()
                    .filter(problem -> !assigned.contains(problem.id()) && range.accepts(rating(problem.difficultyRating())))
                    .forEach(candidates::add);
        } while (++number < page.totalPages() && number < MAX_PAGES);
        return candidates.stream()
                .sorted(Comparator.comparing((ProblemResponse p) -> rating(p.difficultyRating()),
                        Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(ProblemResponse::title))
                .filter(problem -> proposed.add(problem.url()))
                .limit(PER_TOPIC)
                .map(problem -> new ProblemRecommendation(problem.id(), problem.title(), problem.url(),
                        topic.topicName(), problem.difficultyRating(), Source.CATALOG,
                        reason(topic, rating(problem.difficultyRating()), range)))
                .toList();
    }

    private static String reason(TopicPerformance topic, Integer difficulty, Range range) {
        String text = "Tema " + topic.topicName() + ": el equipo resolvió " + topic.solvedProblems() + " de "
                + topic.assignedProblems() + " problemas. ";
        return text + (difficulty == null ? "Sin dificultad registrada." : "Dificultad " + difficulty
                + range.describe() + ".");
    }

    private static Integer rating(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value.strip());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String fold(String value) {
        return Normalizer.normalize(value.strip(), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    /** Codeforces is only consulted when needed, at most once per request, and its failure is not fatal. */
    private final class Codeforces {
        private List<CodeforcesProblem> problems;
        private boolean failed;

        private List<ProblemRecommendation> options(TopicPerformance topic, Range range, int limit,
                                                    Set<String> proposed) {
            List<CodeforcesProblem> candidates = load(codeforcesCatalog::problems).stream()
                    .filter(problem -> problem.tags().stream().anyMatch(tag -> fold(tag).equals(fold(topic.topicName()))))
                    .filter(problem -> range.accepts(problem.rating()))
                    .sorted(Comparator.comparing(CodeforcesProblem::rating,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .limit(CODEFORCES_CANDIDATES)
                    .toList();
            if (candidates.isEmpty()) {
                return List.of();
            }
            Map<String, Integer> registered = problemService.findIdsBySourceCodes(SourcePlatform.CODEFORCES,
                    candidates.stream().map(CodeforcesProblem::code).toList());
            return candidates.stream()
                    .filter(problem -> !registered.containsKey(problem.code().toUpperCase(Locale.ROOT)))
                    .filter(problem -> proposed.add(url(problem)))
                    .limit(limit)
                    .map(problem -> new ProblemRecommendation(null, problem.name(), url(problem), topic.topicName(),
                            problem.rating() == null ? null : problem.rating().toString(), Source.CODEFORCES,
                            reason(topic, problem.rating(), range)
                                    + " Aún no está en el catálogo: impórtalo antes de asignarlo."))
                    .toList();
        }

        private List<CodeforcesProblem> load(Supplier<List<CodeforcesProblem>> source) {
            if (problems == null) {
                try {
                    problems = source.get();
                } catch (CodeforcesUnavailableException exception) {
                    failed = true;
                    problems = List.of();
                }
            }
            return problems;
        }

        private String url(CodeforcesProblem problem) {
            return "https://codeforces.com/problemset/problem/" + problem.contestId() + "/" + problem.index();
        }
    }

    private record Range(Integer min, Integer max) {
        /** Without a range everything fits; with one, an unrated problem cannot be called compatible. */
        boolean accepts(Integer rating) {
            if (min == null && max == null) {
                return true;
            }
            return rating != null && (min == null || rating >= min) && (max == null || rating <= max);
        }

        String describe() {
            if (min != null && max != null) {
                return " (rango " + min + " a " + max + ")";
            }
            if (min != null) {
                return " (desde " + min + ")";
            }
            return max != null ? " (hasta " + max + ")" : "";
        }
    }
}

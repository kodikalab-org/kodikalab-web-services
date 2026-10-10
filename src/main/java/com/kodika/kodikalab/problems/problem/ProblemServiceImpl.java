package com.kodika.kodikalab.problems.problem;

import com.kodika.kodikalab.common.exception.FieldConflictException;
import com.kodika.kodikalab.common.exception.FieldValidationException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.problems.problem.dto.CreateProblemRequest;
import com.kodika.kodikalab.problems.problem.dto.ProblemPageResponse;
import com.kodika.kodikalab.problems.problem.dto.ProblemResponse;
import com.kodika.kodikalab.problems.problem.dto.ProblemResponse.TopicInfo;
import com.kodika.kodikalab.problems.problem.dto.ProblemSearch;
import com.kodika.kodikalab.problems.problem.dto.ProblemSummary;
import com.kodika.kodikalab.problems.problemtopic.ProblemTopic;
import com.kodika.kodikalab.problems.problemtopic.ProblemTopicRepository;
import com.kodika.kodikalab.problems.problemtopic.dto.ProblemTopicData;
import com.kodika.kodikalab.problems.topic.Topic;
import com.kodika.kodikalab.problems.topic.TopicService;
import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserStatus;
import jakarta.persistence.EntityManager;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProblemServiceImpl implements ProblemService {
    static final int MAX_TITLE = 150;
    static final int MAX_URL = 300;
    static final int MAX_SOURCE_CODE = 50;
    static final int MAX_DIFFICULTY = 30;
    static final int MAX_TOPIC_NAME = 80;
    static final int MAX_TOPICS = 20;
    static final int MAX_QUERY = 100;
    static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_TIME_LIMIT_MS = 1000;
    private static final int DEFAULT_MEMORY_LIMIT_MB = 256;

    private final ProblemRepository problemRepository;
    private final ProblemTopicRepository problemTopicRepository;
    private final TopicService topicService;
    private final CurrentUserResolver currentUserResolver;
    private final EntityManager entityManager;

    public ProblemServiceImpl(ProblemRepository problemRepository, ProblemTopicRepository problemTopicRepository,
                              TopicService topicService, CurrentUserResolver currentUserResolver,
                              EntityManager entityManager) {
        this.problemRepository = problemRepository;
        this.problemTopicRepository = problemTopicRepository;
        this.topicService = topicService;
        this.currentUserResolver = currentUserResolver;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public ProblemResponse create(CreateProblemRequest request) {
        User user = activeUser();
        if (user.getRole() != Role.COACH) {
            throw new ForbiddenException("Solo un coach activo puede registrar problemas en el catálogo");
        }
        if (request == null) {
            throw new FieldValidationException("Los datos del problema deben corregirse",
                    Map.of("body", "Debe enviar los datos del problema"));
        }

        Map<String, String> errors = new LinkedHashMap<>();
        String title = request.title() == null ? "" : request.title().strip();
        if (title.isEmpty() || title.length() > MAX_TITLE) {
            errors.put("title", "El título es obligatorio y debe tener como máximo " + MAX_TITLE + " caracteres");
        }
        String url = request.url() == null ? "" : request.url().strip();
        if (url.isEmpty() || url.length() > MAX_URL || !isHttpUrl(url)) {
            errors.put("url", "La URL es obligatoria, de hasta " + MAX_URL
                    + " caracteres, y debe ser http o https sin credenciales");
        }
        String sourceCode = blankToNull(request.sourceCode());
        if (sourceCode != null && sourceCode.length() > MAX_SOURCE_CODE) {
            errors.put("sourceCode", "El código de origen debe tener como máximo " + MAX_SOURCE_CODE + " caracteres");
        }
        String difficulty = blankToNull(request.difficultyRating());
        if (difficulty != null && difficulty.length() > MAX_DIFFICULTY) {
            errors.put("difficultyRating", "La dificultad debe tener como máximo " + MAX_DIFFICULTY + " caracteres");
        }
        int timeLimitMs = request.timeLimitMs() == null ? DEFAULT_TIME_LIMIT_MS : request.timeLimitMs();
        if (timeLimitMs < 1) {
            errors.put("timeLimitMs", "El límite de tiempo debe ser de al menos 1 ms");
        }
        int memoryLimitMb = request.memoryLimitMb() == null ? DEFAULT_MEMORY_LIMIT_MB : request.memoryLimitMb();
        if (memoryLimitMb < 1) {
            errors.put("memoryLimitMb", "El límite de memoria debe ser de al menos 1 MB");
        }
        List<String> topicNames = normalizeTopics(request.topics(), errors);
        if (!errors.isEmpty()) {
            throw new FieldValidationException("Los datos del problema deben corregirse", errors);
        }

        SourcePlatform platform = request.sourcePlatform() == null ? SourcePlatform.CODEFORCES
                : request.sourcePlatform();
        Map<String, String> conflicts = new LinkedHashMap<>();
        if (sourceCode != null && problemRepository.existsBySourcePlatformAndSourceCodeIgnoreCase(platform, sourceCode)) {
            conflicts.put("sourceCode", "Ya existe un problema de " + platform + " con ese código de origen");
        }
        if (problemRepository.existsByUrlIgnoreCase(url)) {
            conflicts.put("url", "Ya existe un problema con esa URL");
        }
        if (!conflicts.isEmpty()) {
            throw new FieldConflictException("El problema ya está registrado en el catálogo", conflicts);
        }

        Problem problem = new Problem();
        problem.setTitle(title);
        problem.setUrl(url);
        problem.setSourcePlatform(platform);
        problem.setSourceCode(sourceCode);
        problem.setDifficultyRating(difficulty);
        problem.setTimeLimitMs(timeLimitMs);
        problem.setMemoryLimitMb(memoryLimitMb);
        problemRepository.save(problem);

        List<Topic> topics = topicService.findOrCreateAll(topicNames);
        for (Topic topic : topics) {
            ProblemTopic link = new ProblemTopic();
            link.setProblem(problem);
            link.setTopic(topic);
            entityManager.persist(link);
        }
        return response(problem, topics.stream().map(topic -> new TopicInfo(topic.getId(), topic.getName())).toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ProblemPageResponse search(ProblemSearch search) {
        activeUser();
        Map<String, String> errors = new LinkedHashMap<>();
        if (search.page() < 0) {
            errors.put("page", "La página no puede ser negativa");
        }
        if (search.size() < 1 || search.size() > MAX_PAGE_SIZE) {
            errors.put("size", "El tamaño de página debe estar entre 1 y " + MAX_PAGE_SIZE);
        }
        String query = blankToNull(search.query());
        if (query != null && query.length() > MAX_QUERY) {
            errors.put("q", "La búsqueda debe tener como máximo " + MAX_QUERY + " caracteres");
        }
        String difficulty = blankToNull(search.difficulty());
        if (difficulty != null && difficulty.length() > MAX_DIFFICULTY) {
            errors.put("difficulty", "La dificultad debe tener como máximo " + MAX_DIFFICULTY + " caracteres");
        }
        if (search.topicId() != null && search.topicId() < 1) {
            errors.put("topicId", "El tema debe ser un entero positivo");
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException("Los criterios de búsqueda deben corregirse", errors);
        }

        Page<Problem> page = problemRepository.findAll(
                specification(query, search.topicId(), difficulty, search.platform()),
                PageRequest.of(search.page(), search.size(), Sort.by("title").and(Sort.by("id"))));
        Map<Integer, List<TopicInfo>> topicsByProblem = new LinkedHashMap<>();
        if (!page.isEmpty()) {
            Set<Integer> ids = new HashSet<>();
            page.forEach(problem -> ids.add(problem.getId()));
            for (ProblemTopicData data : problemTopicRepository.findTopicsByProblemIds(ids)) {
                topicsByProblem.computeIfAbsent(data.problemId(), id -> new ArrayList<>())
                        .add(new TopicInfo(data.topicId(), data.topicName()));
            }
        }
        List<ProblemResponse> items = page.getContent().stream()
                .map(problem -> response(problem, topicsByProblem.getOrDefault(problem.getId(), List.of())))
                .toList();
        return new ProblemPageResponse(items, page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProblemSummary> findSummariesByIds(Collection<Integer> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return problemRepository.findAllById(ids).stream()
                .map(problem -> new ProblemSummary(problem.getId(), problem.getTitle(), problem.getUrl(),
                        problem.getSourcePlatform(), problem.getSourceCode(), problem.getDifficultyRating(),
                        problem.getTimeLimitMs(), problem.getMemoryLimitMb()))
                .toList();
    }

    private User activeUser() {
        User user = currentUserResolver.currentUser();
        if (user.getStatus() != UserStatus.ACTIVO) {
            throw new ForbiddenException("La cuenta no está activa");
        }
        return user;
    }

    private static Specification<Problem> specification(String query, Integer topicId, String difficulty,
                                                        SourcePlatform platform) {
        Specification<Problem> spec = (root, criteria, cb) -> cb.conjunction();
        if (query != null) {
            String pattern = "%" + escapeLike(query.toLowerCase(Locale.ROOT)) + "%";
            spec = spec.and((root, criteria, cb) -> cb.or(
                    cb.like(cb.lower(root.get("title")), pattern, '\\'),
                    cb.like(cb.lower(root.get("sourceCode")), pattern, '\\')));
        }
        if (difficulty != null) {
            spec = spec.and((root, criteria, cb) -> cb.equal(root.get("difficultyRating"), difficulty));
        }
        if (platform != null) {
            spec = spec.and((root, criteria, cb) -> cb.equal(root.get("sourcePlatform"), platform));
        }
        if (topicId != null) {
            spec = spec.and((root, criteria, cb) -> {
                var sub = criteria.subquery(Integer.class);
                var link = sub.from(ProblemTopic.class);
                sub.select(cb.literal(1)).where(cb.equal(link.get("problem"), root),
                        cb.equal(link.get("topic").get("id"), topicId));
                return cb.exists(sub);
            });
        }
        return spec;
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static List<String> normalizeTopics(List<String> topics, Map<String, String> errors) {
        if (topics == null) {
            return List.of();
        }
        if (topics.size() > MAX_TOPICS) {
            errors.put("topics", "Se admiten como máximo " + MAX_TOPICS + " temas por problema");
            return List.of();
        }
        List<String> names = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < topics.size(); index++) {
            String name = topics.get(index).strip();
            if (name.isEmpty() || name.length() > MAX_TOPIC_NAME) {
                errors.put("topics[" + index + "]",
                        "El tema es obligatorio y debe tener como máximo " + MAX_TOPIC_NAME + " caracteres");
            } else if (seen.add(name.toLowerCase(Locale.ROOT))) {
                names.add(name);
            }
        }
        return names;
    }

    private static boolean isHttpUrl(String value) {
        try {
            URI uri = new URI(value);
            return uri.getHost() != null && uri.getUserInfo() == null
                    && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static ProblemResponse response(Problem problem, List<TopicInfo> topics) {
        return new ProblemResponse(problem.getId(), problem.getTitle(), problem.getUrl(), problem.getSourcePlatform(),
                problem.getSourceCode(), problem.getDifficultyRating(), problem.getTimeLimitMs(),
                problem.getMemoryLimitMb(), topics);
    }
}

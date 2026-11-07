package com.kodika.kodikalab.profiles.practitioner.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kodika.kodikalab.common.exception.NotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CodeforcesApiClient implements CodeforcesClient, CodeforcesProblemClient {
    private static final Duration DEFAULT_CATALOG_TIMEOUT = Duration.ofSeconds(30);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String baseUrl;
    private final Duration timeout;
    private final Duration catalogTimeout;
    private long nextRequestNanos;

    public CodeforcesApiClient(ObjectMapper objectMapper, String baseUrl, long timeoutMs) {
        this(objectMapper, baseUrl, timeoutMs, DEFAULT_CATALOG_TIMEOUT.toMillis());
    }

    @Autowired
    public CodeforcesApiClient(ObjectMapper objectMapper,
                               @Value("${kodikalab.integrations.codeforces.base-url:https://codeforces.com/api}")
                               String baseUrl,
                               @Value("${kodikalab.integrations.codeforces.timeout-ms:3000}")
                               long timeoutMs,
                               @Value("${kodikalab.integrations.codeforces.catalog-timeout-ms:30000}")
                               long catalogTimeoutMs) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.timeout = Duration.ofMillis(timeoutMs);
        // El problemset completo pesa varios MB: necesita más margen que una consulta puntual.
        this.catalogTimeout = Duration.ofMillis(catalogTimeoutMs);
    }

    @Override
    public Optional<CodeforcesUserInfo> findUser(String handle) {
        if (handle == null || handle.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(requireUser(handle));
        } catch (RuntimeException exception) {
            // Profile saves remain available when the external handle cannot be confirmed.
            return Optional.empty();
        }
    }

    @Override
    public CodeforcesUserInfo requireUser(String handle) {
        JsonNode users = request("/user.info?handles=" + encode(handle));
        JsonNode user = users.isArray() && !users.isEmpty() ? users.get(0) : null;
        if (user == null || !user.path("handle").isTextual()) {
            throw new CodeforcesUnavailableException();
        }
        return new CodeforcesUserInfo(user.get("handle").asText(),
                user.hasNonNull("rating") ? user.get("rating").asInt() : null);
    }

    @Override
    public List<CodeforcesSubmission> findSubmissions(String handle) {
        // Omitting count returns the complete history, not only a recent page.
        JsonNode submissions = request("/user.status?handle=" + encode(handle) + "&from=1");
        List<CodeforcesSubmission> result = new ArrayList<>();
        for (JsonNode submission : submissions) {
            JsonNode problem = submission.path("problem");
            if (!submission.path("creationTimeSeconds").isIntegralNumber() || !problem.path("index").isTextual()) {
                throw new CodeforcesUnavailableException();
            }
            result.add(new CodeforcesSubmission(submission.get("creationTimeSeconds").asLong(),
                    problem.hasNonNull("contestId") ? problem.get("contestId").asInt() : null,
                    problem.get("index").asText(),
                    problem.hasNonNull("rating") ? problem.get("rating").asInt() : null,
                    submission.path("verdict").asText(null)));
        }
        return List.copyOf(result);
    }

    @Override
    public Optional<CodeforcesProblem> findProblem(int contestId, String index) {
        // Codeforces solo acepta contest.standings sin parámetros extra para concursos que no son gym; es el respaldo
        // para problemas que todavía no aparecen en problemset.problems, por eso usa el timeout del catálogo.
        JsonNode result = fetchResult("/contest.standings?contestId=" + contestId, catalogTimeout,
                comment -> comment.contains("contest") && comment.contains("not found"));
        if (result == null) {
            return Optional.empty();
        }
        JsonNode problems = result.path("problems");
        if (!problems.isArray()) {
            throw new CodeforcesUnavailableException();
        }
        for (JsonNode problem : problems) {
            if (index.equalsIgnoreCase(problem.path("index").asText(""))) {
                return Optional.of(toProblem(problem, contestId));
            }
        }
        return Optional.empty();
    }

    @Override
    public List<CodeforcesProblem> listProblems() {
        JsonNode result = fetchResult("/problemset.problems", catalogTimeout, comment -> false);
        JsonNode problems = result == null ? null : result.path("problems");
        if (problems == null || !problems.isArray()) {
            throw new CodeforcesUnavailableException();
        }
        List<CodeforcesProblem> list = new ArrayList<>();
        for (JsonNode problem : problems) {
            if (!problem.hasNonNull("contestId")) {
                continue; // problemas sin concurso público (no se pueden enlazar)
            }
            list.add(toProblem(problem, problem.get("contestId").asInt()));
        }
        return List.copyOf(list);
    }

    private static CodeforcesProblem toProblem(JsonNode problem, int contestId) {
        if (!problem.path("index").isTextual() || !problem.path("name").isTextual()) {
            throw new CodeforcesUnavailableException();
        }
        List<String> tags = new ArrayList<>();
        for (JsonNode tag : problem.path("tags")) {
            if (tag.isTextual()) {
                tags.add(tag.asText());
            }
        }
        return new CodeforcesProblem(contestId, problem.get("index").asText().toUpperCase(Locale.ROOT),
                problem.get("name").asText(), problem.hasNonNull("rating") ? problem.get("rating").asInt() : null,
                tags);
    }

    private String encode(String handle) {
        return URLEncoder.encode(handle.trim(), StandardCharsets.UTF_8);
    }

    private JsonNode request(String path) {
        JsonNode result = fetchResult(path, timeout, comment -> comment.contains("user with handle")
                && comment.contains("not found"));
        if (result == null) {
            throw new NotFoundException("El usuario de Codeforces no existe");
        }
        if (!result.isArray()) {
            throw new CodeforcesUnavailableException();
        }
        return result;
    }

    /**
     * Pide {@code path} y devuelve el nodo {@code result}; {@code null} si Codeforces responde {@code FAILED} con un
     * comentario (en minúsculas) que cumple {@code notFound}. Cualquier otro fallo (red, 429/5xx, cuerpo ilegible,
     * otro {@code FAILED}) es {@link CodeforcesUnavailableException}.
     */
    private JsonNode fetchResult(String path, Duration requestTimeout, Predicate<String> notFound) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(requestTimeout).GET()
                    .build();
            HttpResponse<String> response = sendSpaced(request);
            if (response.statusCode() >= 500 || response.statusCode() == 429) {
                throw new CodeforcesUnavailableException();
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (root == null) {
                throw new CodeforcesUnavailableException();
            }
            if (!"OK".equals(root.path("status").asText())) {
                String comment = root.path("comment").asText("").toLowerCase(Locale.ROOT);
                if (notFound.test(comment)) {
                    return null;
                }
                throw new CodeforcesUnavailableException();
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300 || !root.hasNonNull("result")) {
                throw new CodeforcesUnavailableException();
            }
            return root.get("result");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CodeforcesUnavailableException();
        } catch (IOException | IllegalArgumentException exception) {
            throw new CodeforcesUnavailableException();
        }
    }

    private synchronized HttpResponse<String> sendSpaced(HttpRequest request) throws IOException, InterruptedException {
        long remaining = nextRequestNanos - System.nanoTime();
        if (remaining > 0) {
            java.util.concurrent.TimeUnit.NANOSECONDS.sleep(remaining);
        }
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } finally {
            // Space from completion: transport initialization can delay upstream arrival.
            nextRequestNanos = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        }
    }
}

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CodeforcesApiClient implements CodeforcesClient {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String baseUrl;
    private final Duration timeout;
    private long nextRequestNanos;

    public CodeforcesApiClient(ObjectMapper objectMapper,
                               @Value("${kodikalab.integrations.codeforces.base-url:https://codeforces.com/api}")
                               String baseUrl,
                               @Value("${kodikalab.integrations.codeforces.timeout-ms:3000}")
                               long timeoutMs) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.timeout = Duration.ofMillis(timeoutMs);
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

    private String encode(String handle) {
        return URLEncoder.encode(handle.trim(), StandardCharsets.UTF_8);
    }

    private JsonNode request(String path) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(timeout).GET().build();
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
                if (comment.contains("user with handle") && comment.contains("not found")) {
                    throw new NotFoundException("El usuario de Codeforces no existe");
                }
                throw new CodeforcesUnavailableException();
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300 || !root.path("result").isArray()) {
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

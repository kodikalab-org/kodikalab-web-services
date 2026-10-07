package com.kodika.kodikalab.profiles.practitioner.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CodeforcesApiClient implements CodeforcesClient {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String baseUrl;
    private final Duration timeout;

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
        String encodedHandle = URLEncoder.encode(handle.trim(), StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/user.info?handles=" + encodedHandle))
                .timeout(timeout)
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Optional.empty();
            }
            return parseUser(response.body());
        } catch (IOException | InterruptedException | RuntimeException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }
    }

    private Optional<CodeforcesUserInfo> parseUser(String body) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        if (!"OK".equals(root.path("status").asText())) {
            return Optional.empty();
        }
        JsonNode firstUser = root.path("result").isArray() && !root.path("result").isEmpty()
                ? root.path("result").get(0) : null;
        if (firstUser == null || firstUser.path("handle").asText(null) == null) {
            return Optional.empty();
        }
        Integer rating = firstUser.hasNonNull("rating") ? firstUser.get("rating").asInt() : null;
        return Optional.of(new CodeforcesUserInfo(firstUser.get("handle").asText(), rating));
    }
}

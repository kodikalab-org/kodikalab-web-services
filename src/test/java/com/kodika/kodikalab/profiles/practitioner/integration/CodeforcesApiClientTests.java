package com.kodika.kodikalab.profiles.practitioner.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Simula la API pública de Codeforces con un servidor HTTP local; no requiere internet. */
class CodeforcesApiClientTests {
    private static final long TIMEOUT_MS = 300;
    HttpServer server;
    CodeforcesApiClient client;
    AtomicInteger status = new AtomicInteger(200);
    AtomicReference<String> body = new AtomicReference<>("");
    AtomicReference<String> lastQuery = new AtomicReference<>();
    AtomicInteger delayMs = new AtomicInteger(0);
    AtomicInteger requests = new AtomicInteger(0);
    java.util.List<Long> receivedAt = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/user.info", exchange -> {
            receivedAt.add(System.nanoTime());
            requests.incrementAndGet();
            lastQuery.set(exchange.getRequestURI().getRawQuery());
            try {
                Thread.sleep(delayMs.get());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            byte[] response = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), response.length == 0 ? -1 : response.length);
            if (response.length > 0) {
                exchange.getResponseBody().write(response);
            }
            exchange.close();
        });
        server.createContext("/api/user.status", exchange -> {
            receivedAt.add(System.nanoTime());
            requests.incrementAndGet();
            lastQuery.set(exchange.getRequestURI().getRawQuery());
            byte[] response = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), response.length == 0 ? -1 : response.length);
            if (response.length > 0) exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        client = new CodeforcesApiClient(new ObjectMapper(),
                "http://127.0.0.1:" + server.getAddress().getPort() + "/api/", TIMEOUT_MS);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void confirmedUserReturnsCanonicalHandleAndRating() {
        body.set("""
                {"status":"OK","result":[{"handle":"Tourist","rating":3800,"rank":"legendary grandmaster"}]}
                """);

        var user = client.findUser("tourist");

        assertThat(user).contains(new CodeforcesUserInfo("Tourist", 3800));
        assertThat(lastQuery.get()).isEqualTo("handles=tourist");
    }

    @Test
    void confirmedUserWithoutRatingReturnsNullRating() {
        body.set("""
                {"status":"OK","result":[{"handle":"newbie_ok"}]}
                """);

        assertThat(client.findUser("newbie_ok")).contains(new CodeforcesUserInfo("newbie_ok", null));
    }

    @Test
    void failedStatusIsNotConfirmed() {
        status.set(400);
        body.set("""
                {"status":"FAILED","comment":"handles: User with handle missing_handle not found"}
                """);

        assertThat(client.findUser("missing_handle")).isEmpty();
    }

    @Test
    void okStatusWithFailedPayloadIsNotConfirmed() {
        body.set("""
                {"status":"FAILED","comment":"Call limit exceeded"}
                """);

        assertThat(client.findUser("tourist")).isEmpty();
    }

    @Test
    void notFoundIsNotConfirmed() {
        status.set(404);

        assertThat(client.findUser("tourist")).isEmpty();
    }

    @Test
    void emptyResultIsNotConfirmed() {
        body.set("""
                {"status":"OK","result":[]}
                """);

        assertThat(client.findUser("tourist")).isEmpty();
    }

    @Test
    void invalidJsonIsNotConfirmed() {
        body.set("<html>Codeforces is temporarily unavailable</html>");

        assertThat(client.findUser("tourist")).isEmpty();
    }

    @Test
    void timeoutIsNotConfirmed() {
        delayMs.set((int) TIMEOUT_MS * 4);
        body.set("""
                {"status":"OK","result":[{"handle":"tourist","rating":3800}]}
                """);

        assertThat(client.findUser("tourist")).isEmpty();
    }

    @Test
    void unreachableServerIsNotConfirmed() {
        server.stop(0);

        assertThat(client.findUser("tourist")).isEmpty();
    }

    @Test
    void handleIsUrlEncoded() {
        body.set("""
                {"status":"OK","result":[{"handle":"a.b-c_d","rating":1200}]}
                """);

        client.findUser("a.b-c_d");

        assertThat(lastQuery.get()).isEqualTo("handles=a.b-c_d");
        client.findUser("x&handles=tourist");
        assertThat(lastQuery.get()).isEqualTo("handles=x%26handles%3Dtourist");
    }

    @Test
    void blankHandleDoesNotCallApi() {
        assertThat(client.findUser("   ")).isEmpty();
        assertThat(client.findUser(null)).isEmpty();
        assertThat(requests.get()).isZero();
    }

    @Test
    void fullSubmissionHistoryUsesSingularHandleAndParsesOptionalRatingAndVerdict() {
        body.set("""
                {"status":"OK","result":[
                  {"creationTimeSeconds":1793966400,"verdict":"OK","problem":{"contestId":1,"index":"A","rating":800}},
                  {"creationTimeSeconds":1793966401,"problem":{"contestId":1,"index":"B"}}]}
                """);
        var submissions = client.findSubmissions("test_handle");
        assertThat(lastQuery.get()).isEqualTo("handle=test_handle&from=1");
        assertThat(submissions).hasSize(2);
        assertThat(submissions.get(0)).isEqualTo(new CodeforcesSubmission(1793966400, 1, "A", 800, "OK"));
        assertThat(submissions.get(1).rating()).isNull();
        assertThat(submissions.get(1).verdict()).isNull();
    }

    @Test
    void strictInfoDistinguishesMissingHandleFromUnavailability() {
        status.set(400);
        body.set("{\"status\":\"FAILED\",\"comment\":\"handles: User with handle missing_handle not found\"}");
        assertThatThrownBy(() -> client.requireUser("missing_handle"))
                .isInstanceOf(com.kodika.kodikalab.common.exception.NotFoundException.class);
        status.set(503);
        body.set("<html>Unavailable</html>");
        assertThatThrownBy(() -> client.requireUser("test_handle")).isInstanceOf(CodeforcesUnavailableException.class);
    }

    @Test
    void malformedSubmissionPayloadIsUnavailableNotZeroStats() {
        body.set("{\"status\":\"OK\",\"result\":[{\"problem\":{\"index\":\"A\"}}]}");
        assertThatThrownBy(() -> client.findSubmissions("test_handle")).isInstanceOf(CodeforcesUnavailableException.class);
    }

    @Test
    void existingProfileValidationAndNewMethodsShareTwoSecondSpacing() {
        client = new CodeforcesApiClient(new ObjectMapper(),
                "http://127.0.0.1:" + server.getAddress().getPort() + "/api/", 3000);
        // Delayed replies distinguish completion-based spacing from send-entry spacing.
        delayMs.set(350);
        body.set("{\"status\":\"OK\",\"result\":[{\"handle\":\"test_handle\"}]}");
        client.findUser("test_handle");
        client.requireUser("test_handle");
        body.set("{\"status\":\"OK\",\"result\":[]}");
        client.findSubmissions("test_handle");
        assertThat(receivedAt).hasSize(3);
        for (int i = 1; i < receivedAt.size(); i++) {
            assertThat(java.time.Duration.ofNanos(receivedAt.get(i) - receivedAt.get(i - 1)).toMillis())
                    .isGreaterThanOrEqualTo(2250);
        }
    }

    @Test
    void strictTimeoutAndRateLimitAreUnavailable() {
        delayMs.set((int) TIMEOUT_MS * 4);
        assertThatThrownBy(() -> client.requireUser("test_handle")).isInstanceOf(CodeforcesUnavailableException.class);
        status.set(429);
        assertThatThrownBy(() -> client.findSubmissions("test_handle")).isInstanceOf(CodeforcesUnavailableException.class);
    }
}

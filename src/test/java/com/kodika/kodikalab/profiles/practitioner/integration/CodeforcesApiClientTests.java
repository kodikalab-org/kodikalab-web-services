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

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/user.info", exchange -> {
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
}

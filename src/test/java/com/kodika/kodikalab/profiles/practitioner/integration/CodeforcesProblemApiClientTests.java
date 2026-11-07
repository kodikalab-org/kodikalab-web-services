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

/** Problemas de Codeforces con un servidor HTTP local que imita contest.standings y problemset.problems. */
class CodeforcesProblemApiClientTests {
    HttpServer server;
    CodeforcesApiClient client;
    AtomicInteger status = new AtomicInteger(200);
    AtomicReference<String> body = new AtomicReference<>("");
    AtomicReference<String> lastPath = new AtomicReference<>();
    AtomicReference<String> lastQuery = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/", exchange -> {
            lastPath.set(exchange.getRequestURI().getPath());
            lastQuery.set(exchange.getRequestURI().getRawQuery());
            byte[] response = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), response.length == 0 ? -1 : response.length);
            if (response.length > 0) {
                exchange.getResponseBody().write(response);
            }
            exchange.close();
        });
        server.start();
        client = new CodeforcesApiClient(new ObjectMapper(),
                "http://127.0.0.1:" + server.getAddress().getPort() + "/api/", 500);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    static final String STANDINGS = """
            {"status":"OK","result":{"contest":{"id":4},"problems":[
              {"contestId":4,"index":"A","name":"Watermelon","type":"PROGRAMMING","rating":800,"tags":["brute force","math"]},
              {"contestId":4,"index":"B1","name":"Split it","type":"PROGRAMMING","tags":[]}],"rows":[]}}
            """;

    @Test
    void findsAProblemInTheContestStandingsWithOneRowRequested() {
        body.set(STANDINGS);

        var problem = client.findProblem(4, "a");

        assertThat(lastPath.get()).isEqualTo("/api/contest.standings");
        assertThat(lastQuery.get()).isEqualTo("contestId=4");
        assertThat(problem).contains(new CodeforcesProblem(4, "A", "Watermelon", 800, java.util.List.of("brute force", "math")));
    }

    @Test
    void aProblemWithoutRatingOrTagsHasNullRatingAndEmptyTags() {
        body.set(STANDINGS);

        assertThat(client.findProblem(4, "B1")).contains(new CodeforcesProblem(4, "B1", "Split it", null, java.util.List.of()));
    }

    @Test
    void anIndexMissingFromTheContestIsEmpty() {
        body.set(STANDINGS);

        assertThat(client.findProblem(4, "Z")).isEmpty();
    }

    @Test
    void aFailedStatusSayingTheContestWasNotFoundIsEmpty() {
        status.set(400);
        body.set("{\"status\":\"FAILED\",\"comment\":\"contestId: Contest with id 99999 not found\"}");

        assertThat(client.findProblem(99999, "A")).isEmpty();
    }

    @Test
    void anyOtherFailureIsUnavailable() {
        body.set("{\"status\":\"FAILED\",\"comment\":\"Call limit exceeded\"}");
        assertThatThrownBy(() -> client.findProblem(4, "A")).isInstanceOf(CodeforcesUnavailableException.class);
        status.set(429);
        assertThatThrownBy(() -> client.findProblem(4, "A")).isInstanceOf(CodeforcesUnavailableException.class);
        status.set(503);
        body.set("<html>down</html>");
        assertThatThrownBy(() -> client.findProblem(4, "A")).isInstanceOf(CodeforcesUnavailableException.class);
    }

    @Test
    void aStandingsPayloadWithoutProblemsIsUnavailable() {
        body.set("{\"status\":\"OK\",\"result\":{\"contest\":{\"id\":4}}}");

        assertThatThrownBy(() -> client.findProblem(4, "A")).isInstanceOf(CodeforcesUnavailableException.class);
    }

    @Test
    void listsTheWholeProblemsetSkippingEntriesWithoutContest() {
        body.set("""
                {"status":"OK","result":{"problems":[
                  {"contestId":1850,"index":"a","name":"To My Critics","rating":800,"tags":["implementation"]},
                  {"index":"A","name":"No contest","tags":[]},
                  {"contestId":4,"index":"A","name":"Watermelon","tags":["math"]}],
                 "problemStatistics":[]}}
                """);

        var problems = client.listProblems();

        assertThat(lastPath.get()).isEqualTo("/api/problemset.problems");
        assertThat(problems).containsExactly(
                new CodeforcesProblem(1850, "A", "To My Critics", 800, java.util.List.of("implementation")),
                new CodeforcesProblem(4, "A", "Watermelon", null, java.util.List.of("math")));
    }

    @Test
    void aProblemsetFailureIsUnavailable() {
        body.set("{\"status\":\"FAILED\",\"comment\":\"contest not found\"}");

        assertThatThrownBy(() -> client.listProblems()).isInstanceOf(CodeforcesUnavailableException.class);
    }
}

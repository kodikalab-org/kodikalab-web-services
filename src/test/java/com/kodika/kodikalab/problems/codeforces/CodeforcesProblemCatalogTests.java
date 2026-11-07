package com.kodika.kodikalab.problems.codeforces;

import com.kodika.kodikalab.profiles.practitioner.integration.CodeforcesProblem;
import com.kodika.kodikalab.profiles.practitioner.integration.CodeforcesProblemClient;
import com.kodika.kodikalab.profiles.practitioner.integration.CodeforcesUnavailableException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CodeforcesProblemCatalogTests {
    static final CodeforcesProblem WATERMELON = new CodeforcesProblem(4, "A", "Watermelon", 800,
            List.of("brute force", "math"));
    static final CodeforcesProblem THEATRE = new CodeforcesProblem(1, "A", "Theatre Square", 1000, List.of("math"));

    MutableClock clock;
    CodeforcesProblemClient client;
    CodeforcesProblemCatalog catalog;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        client = mock(CodeforcesProblemClient.class);
        catalog = new CodeforcesProblemCatalog(client, Duration.ofHours(6), clock);
    }

    @Test
    void downloadsOnFirstUseAndServesFromMemoryUntilTheTtlExpires() {
        when(client.listProblems()).thenReturn(List.of(WATERMELON));

        assertThat(catalog.problems()).containsExactly(WATERMELON);
        clock.advance(Duration.ofHours(5).plusMinutes(59));
        assertThat(catalog.problems()).containsExactly(WATERMELON);

        verify(client, times(1)).listProblems();
    }

    @Test
    void refreshesLazilyOnceTheTtlExpires() {
        when(client.listProblems()).thenReturn(List.of(WATERMELON)).thenReturn(List.of(WATERMELON, THEATRE));
        catalog.problems();

        clock.advance(Duration.ofHours(6));

        assertThat(catalog.problems()).containsExactly(WATERMELON, THEATRE);
        verify(client, times(2)).listProblems();
    }

    @Test
    void keepsServingTheStaleCopyWhenTheRefreshFailsAndBacksOffBeforeRetrying() {
        when(client.listProblems()).thenReturn(List.of(WATERMELON)).thenThrow(new CodeforcesUnavailableException());
        catalog.problems();
        clock.advance(Duration.ofHours(7));

        assertThat(catalog.problems()).containsExactly(WATERMELON);
        assertThat(catalog.problems()).containsExactly(WATERMELON);
        verify(client, times(2)).listProblems(); // the second stale read did not hit Codeforces again

        clock.advance(CodeforcesProblemCatalog.RETRY_AFTER_FAILURE);
        doReturn(List.of(THEATRE)).when(client).listProblems();

        assertThat(catalog.problems()).containsExactly(THEATRE);
    }

    @Test
    void failsWhenThereIsNoCopyAndCodeforcesIsDown() {
        when(client.listProblems()).thenThrow(new CodeforcesUnavailableException());

        assertThatThrownBy(catalog::problems).isInstanceOf(CodeforcesUnavailableException.class);
        assertThatThrownBy(catalog::problems).isInstanceOf(CodeforcesUnavailableException.class);
        verify(client, times(2)).listProblems(); // a failure is never cached when there is nothing to serve
    }

    @Test
    void peekNeverDownloadsAndSeesTheCopyEvenWhenStale() {
        assertThat(catalog.peek(4, "A")).isEmpty();
        verify(client, times(0)).listProblems();

        when(client.listProblems()).thenReturn(List.of(WATERMELON));
        catalog.problems();
        clock.advance(Duration.ofDays(2));

        assertThat(catalog.peek(4, "a")).contains(WATERMELON);
        assertThat(catalog.peek(4, "B")).isEmpty();
        verify(client, times(1)).listProblems();
    }

    static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}

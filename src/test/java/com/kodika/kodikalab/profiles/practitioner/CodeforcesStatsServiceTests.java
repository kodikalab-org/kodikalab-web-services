package com.kodika.kodikalab.profiles.practitioner;

import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.profiles.practitioner.integration.CodeforcesClient;
import com.kodika.kodikalab.profiles.practitioner.integration.CodeforcesSubmission;
import com.kodika.kodikalab.profiles.practitioner.integration.CodeforcesUnavailableException;
import com.kodika.kodikalab.profiles.practitioner.integration.CodeforcesUserInfo;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CodeforcesStatsServiceTests {
    PractitionerProfileRepository profiles = mock(PractitionerProfileRepository.class);
    FakeClient client = new FakeClient();
    MutableClock clock = new MutableClock();
    CodeforcesStatsService service = new CodeforcesStatsService(profiles, client, clock);
    User user = new User();
    PractitionerProfile profile = new PractitionerProfile();

    @BeforeEach
    void setUp() {
        user.setId(7);
        user.setRole(Role.PRACTICANTE);
        profile.setCodeforcesHandle("test_handle");
        when(profiles.findByUserId(7)).thenReturn(Optional.of(profile));
    }

    @Test
    void distinctProblemsAcceptedAttemptsUtcStreakAndExactBuckets() {
        client.submissions = List.of(
                submission("2024-01-01T23:59:59Z", 1, "A", 800, "OK"),
                submission("2024-01-02T00:00:00Z", 1, "A", 800, "OK"),
                submission("2024-01-03T22:00:00Z", 2, "A", null, "OK"),
                submission("2026-11-05T23:59:59Z", 3, "B", 1200, "OK"),
                submission("2026-11-06T00:00:00Z", 3, "B", 1200, "OK"),
                submission("2026-11-06T01:00:00Z", 4, "A", 800, "WRONG_ANSWER"),
                submission("2026-11-06T02:00:00Z", 4, "A", 800, null));
        var stats = service.getStats(user);
        assertThat(stats.linked()).isTrue();
        assertThat(stats.handle()).isEqualTo("Test_Handle");
        assertThat(stats.problemsSolved()).isEqualTo(3);
        assertThat(stats.acceptedSubmissions()).isEqualTo(5);
        assertThat(stats.acceptanceRate()).isCloseTo(500.0 / 7, within(0.00001));
        assertThat(stats.maxStreakDays()).isEqualTo(3);
        assertThat(stats.currentRating()).isEqualTo(1420);
        assertThat(stats.solvedByRating()).extracting("rating").containsExactly(0, 800, 1200);
        assertThat(stats.solvedByRating()).extracting("count").containsExactly(1, 1, 1);
        assertThat(stats.activity()).extracting("date").containsExactly(
                java.time.LocalDate.parse("2026-11-05"), java.time.LocalDate.parse("2026-11-06"));
    }

    @Test
    void emptyAndUnratedAccountsReturnNumericZeros() {
        client.rating = null;
        var stats = service.getStats(user);
        assertThat(stats.problemsSolved()).isZero();
        assertThat(stats.currentRating()).isZero();
        assertThat(stats.maxStreakDays()).isZero();
        assertThat(stats.acceptanceRate()).isZero();
        assertThat(stats.activity()).isEmpty();
        assertThat(stats.solvedByRating()).isEmpty();
    }

    @Test
    void missingProfileOrHandleNeverCallsUpstream() {
        profile.setCodeforcesHandle(null);
        assertThat(service.getStats(user).linked()).isFalse();
        assertThat(service.getStats(user).handle()).isNull();
        when(profiles.findByUserId(7)).thenReturn(Optional.empty());
        assertThat(service.getStats(user).linked()).isFalse();
        assertThat(client.calls).isZero();
    }

    @Test
    void coachIsForbiddenBeforeRepositoryOrUpstream() {
        user.setRole(Role.COACH);
        assertThatThrownBy(() -> service.getStats(user)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(profiles);
        assertThat(client.calls).isZero();
    }

    @Test
    void cacheIsPerHandleCaseInsensitiveAndExpiresAfterFifteenMinutes() {
        var first = service.getStats(user);
        clock.now = clock.now.plusSeconds(899);
        profile.setCodeforcesHandle("TEST_HANDLE");
        assertThat(service.getStats(user)).isSameAs(first);
        assertThat(client.calls).isEqualTo(1);
        clock.now = clock.now.plusSeconds(1);
        service.getStats(user);
        assertThat(client.calls).isEqualTo(2);
        profile.setCodeforcesHandle("another_handle");
        service.getStats(user);
        assertThat(client.calls).isEqualTo(3);
    }

    @Test
    void failuresAreNotCachedAndDoNotChangeProfile() {
        client.failure = new NotFoundException("missing");
        assertThatThrownBy(() -> service.getStats(user)).isInstanceOf(NotFoundException.class);
        client.failure = new CodeforcesUnavailableException();
        assertThatThrownBy(() -> service.getStats(user)).isInstanceOf(CodeforcesUnavailableException.class);
        client.failure = null;
        assertThat(service.getStats(user).linked()).isTrue();
        assertThat(profile.getCodeforcesHandle()).isEqualTo("test_handle");
        verify(profiles, never()).save(any());
        assertThat(client.calls).isEqualTo(3);
    }

    @Test
    void submissionFailureDoesNotCacheAnIncompleteResult() {
        client.submissionFailure = new CodeforcesUnavailableException();
        assertThatThrownBy(() -> service.getStats(user)).isInstanceOf(CodeforcesUnavailableException.class);
        client.submissionFailure = null;
        assertThat(service.getStats(user).linked()).isTrue();
        assertThat(client.calls).isEqualTo(2);
        verify(profiles, never()).save(any());
    }

    @Test
    void activityIncludesOnlyLastCalendarYearUtcAndRefreshesAtMidnight() {
        client.submissions = List.of(
                submission("2025-11-06T23:59:59Z", 1, "A", 800, "OK"),
                submission("2025-11-07T00:00:00Z", 2, "A", 900, "OK"));
        var first = service.getStats(user);
        assertThat(first.activity()).hasSize(1);
        clock.now = Instant.parse("2026-11-06T23:59:59Z");
        service.getStats(user);
        clock.now = Instant.parse("2026-11-07T00:00:01Z");
        assertThat(service.getStats(user).activity()).isEmpty();
        assertThat(client.calls).isEqualTo(3);
    }

    private CodeforcesSubmission submission(String time, int contest, String index, Integer rating, String verdict) {
        return new CodeforcesSubmission(Instant.parse(time).getEpochSecond(), contest, index, rating, verdict);
    }

    static class FakeClient implements CodeforcesClient {
        int calls;
        Integer rating = 1420;
        RuntimeException failure;
        RuntimeException submissionFailure;
        List<CodeforcesSubmission> submissions = List.of();

        public Optional<CodeforcesUserInfo> findUser(String handle) {
            calls++;
            if (failure != null) throw failure;
            return Optional.of(new CodeforcesUserInfo("Test_Handle", rating));
        }

        public List<CodeforcesSubmission> findSubmissions(String handle) {
            if (submissionFailure != null) throw submissionFailure;
            return submissions;
        }
    }

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-06T12:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
}

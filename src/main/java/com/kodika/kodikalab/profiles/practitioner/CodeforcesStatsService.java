package com.kodika.kodikalab.profiles.practitioner;

import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.profiles.practitioner.dto.CodeforcesStatsResponse;
import com.kodika.kodikalab.profiles.practitioner.dto.CodeforcesStatsResponse.Activity;
import com.kodika.kodikalab.profiles.practitioner.dto.CodeforcesStatsResponse.SolvedByRating;
import com.kodika.kodikalab.profiles.practitioner.integration.CodeforcesClient;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CodeforcesStatsService {
    private static final Duration CACHE_TTL = Duration.ofMinutes(15);
    private final PractitionerProfileRepository profiles;
    private final CodeforcesClient client;
    private final Clock clock;
    private final Map<String, CachedStats> cache = new LinkedHashMap<>();

    @Autowired
    public CodeforcesStatsService(PractitionerProfileRepository profiles, CodeforcesClient client) {
        this(profiles, client, Clock.systemUTC());
    }

    CodeforcesStatsService(PractitionerProfileRepository profiles, CodeforcesClient client, Clock clock) {
        this.profiles = profiles;
        this.client = client;
        this.clock = clock;
    }

    public CodeforcesStatsResponse getStats(User user) {
        if (user.getRole() != Role.PRACTICANTE) {
            throw new ForbiddenException("Solo los practicantes pueden consultar estas estadísticas");
        }
        String handle = profiles.findByUserId(user.getId()).map(PractitionerProfile::getCodeforcesHandle).orElse(null);
        if (handle == null || handle.isBlank()) {
            return CodeforcesStatsResponse.unlinked();
        }
        return cachedStats(handle.trim());
    }

    private synchronized CodeforcesStatsResponse cachedStats(String handle) {
        Instant now = clock.instant();
        String key = handle.toLowerCase(Locale.ROOT);
        CachedStats cached = cache.get(key);
        if (cached != null && now.isBefore(cached.expiresAt())
                && LocalDate.now(clock).equals(cached.date())) {
            return cached.response();
        }
        // No database transaction is kept open while waiting for upstream HTTP.
        var user = client.requireUser(handle);
        var submissions = client.findSubmissions(user.handle());
        var solved = new HashSet<String>();
        var days = new TreeMap<LocalDate, Integer>();
        var ratings = new TreeMap<Integer, Integer>();
        int accepted = 0;
        for (var submission : submissions) {
            if (!"OK".equals(submission.verdict())) {
                continue;
            }
            accepted++;
            LocalDate date = Instant.ofEpochSecond(submission.creationTimeSeconds()).atZone(ZoneOffset.UTC).toLocalDate();
            days.merge(date, 1, Integer::sum);
            if (submission.contestId() != null && solved.add(submission.contestId() + ":" + submission.index())) {
                ratings.merge(submission.rating() == null ? 0 : submission.rating(), 1, Integer::sum);
            }
        }
        int maximum = 0;
        int streak = 0;
        LocalDate previous = null;
        for (LocalDate day : days.keySet()) {
            streak = previous != null && previous.plusDays(1).equals(day) ? streak + 1 : 1;
            maximum = Math.max(maximum, streak);
            previous = day;
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate start = today.minusYears(1).plusDays(1);
        var activity = days.subMap(start, true, today, true).entrySet().stream()
                .map(entry -> new Activity(entry.getKey(), entry.getValue())).toList();
        var buckets = ratings.entrySet().stream().map(entry -> new SolvedByRating(entry.getKey(), entry.getValue())).toList();
        var response = new CodeforcesStatsResponse(true, user.handle(), solved.size(), maximum,
                user.rating() == null ? 0 : user.rating(), accepted,
                submissions.isEmpty() ? 0 : accepted * 100.0 / submissions.size(), activity, buckets);
        cache.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
        if (cache.size() >= 512) {
            cache.remove(cache.keySet().iterator().next());
        }
        cache.put(key, new CachedStats(response, clock.instant().plus(CACHE_TTL), today));
        return response;
    }

    private record CachedStats(CodeforcesStatsResponse response, Instant expiresAt, LocalDate date) { }
}

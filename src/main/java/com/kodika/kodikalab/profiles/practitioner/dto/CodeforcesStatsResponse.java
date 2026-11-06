package com.kodika.kodikalab.profiles.practitioner.dto;

import java.time.LocalDate;
import java.util.List;

public record CodeforcesStatsResponse(boolean linked, String handle, int problemsSolved, int maxStreakDays,
        int currentRating, int acceptedSubmissions, double acceptanceRate,
        List<Activity> activity, List<SolvedByRating> solvedByRating) {
    public record Activity(LocalDate date, int count) { }
    public record SolvedByRating(int rating, int count) { }

    public static CodeforcesStatsResponse unlinked() {
        return new CodeforcesStatsResponse(false, null, 0, 0, 0, 0, 0, List.of(), List.of());
    }
}

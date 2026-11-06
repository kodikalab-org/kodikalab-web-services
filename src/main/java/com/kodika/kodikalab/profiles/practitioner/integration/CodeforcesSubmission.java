package com.kodika.kodikalab.profiles.practitioner.integration;

public record CodeforcesSubmission(long creationTimeSeconds, Integer contestId, String index,
                                   Integer rating, String verdict) {
}

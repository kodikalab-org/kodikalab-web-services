package com.kodika.kodikalab.analytics.dto;

import com.kodika.kodikalab.competitions.problemresolution.dto.TeamResolutionData;

public record TeamProgressResponse(Integer teamId, Integer membershipId, Integer userId, int acceptedProblems) {
    public record Registration(TeamResolutionData resolution, String registrationMethod, TeamProgressResponse progress) {
    }
}

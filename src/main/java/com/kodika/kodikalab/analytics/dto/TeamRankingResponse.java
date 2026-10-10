package com.kodika.kodikalab.analytics.dto;

import java.util.List;

public record TeamRankingResponse(Integer teamId, Status status, String orderingCriterion,
                                  String tieCriterion, List<MemberStanding> members) {
    public TeamRankingResponse {
        members = List.copyOf(members);
    }

    public enum Status {
        CALCULATED, NO_ACTIVITY
    }

    public record MemberStanding(Integer membershipId, Integer userId, String fullName,
                                 int acceptedProblems, int position) {
    }
}

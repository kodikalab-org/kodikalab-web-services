package com.kodika.kodikalab.analytics.dto;

public record RankingMemberData(Integer membershipId, Integer teamId, Integer userId,
                                String fullName, String status) {
}

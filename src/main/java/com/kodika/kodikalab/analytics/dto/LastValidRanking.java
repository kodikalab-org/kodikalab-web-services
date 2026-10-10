package com.kodika.kodikalab.analytics.dto;

import java.time.OffsetDateTime;

public record LastValidRanking(OffsetDateTime calculatedAt, TeamRankingResponse ranking) {
}

package com.kodika.kodikalab.analytics;

import com.kodika.kodikalab.analytics.dto.LastValidRanking;

public class RankingRecoveryException extends RuntimeException {
    private final transient LastValidRanking lastValidRanking;

    public RankingRecoveryException(RuntimeException cause, LastValidRanking lastValidRanking) {
        super(cause.getMessage(), cause);
        this.lastValidRanking = lastValidRanking;
    }

    public LastValidRanking getLastValidRanking() {
        return lastValidRanking;
    }
}

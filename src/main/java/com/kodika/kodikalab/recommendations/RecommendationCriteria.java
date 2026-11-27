package com.kodika.kodikalab.recommendations;

/** Lo que el coach pidió; los campos {@code null} no restringen. */
public record RecommendationCriteria(Integer minDifficulty, Integer maxDifficulty, String topic) {
    public static final RecommendationCriteria NONE = new RecommendationCriteria(null, null, null);
}

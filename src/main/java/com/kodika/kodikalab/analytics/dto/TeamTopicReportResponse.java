package com.kodika.kodikalab.analytics.dto;

import java.math.BigDecimal;
import java.util.List;

public record TeamTopicReportResponse(Integer teamId, String metric, String comparisonCriterion,
                                      String comparisonExplanation, int activeMembers, int pendingResolutions,
                                      List<TopicPerformance> topics) {
    public TeamTopicReportResponse {
        topics = List.copyOf(topics);
    }

    public record TopicPerformance(Integer topicId, String topicName, int assignedProblems, int solvedProblems,
                                   int unsolvedProblems, int solvingMembers, int pendingResolutions,
                                   BigDecimal coveragePercentage, boolean lowestCoverage) {
    }
}

package com.kodika.kodikalab.problems.problem.dto;

import com.kodika.kodikalab.problems.problem.SourcePlatform;
import java.util.List;

public record ProblemResponse(Integer id, String title, String url, SourcePlatform sourcePlatform, String sourceCode,
                              String difficultyRating, Integer timeLimitMs, Integer memoryLimitMb,
                              List<TopicInfo> topics) {
    public ProblemResponse {
        topics = List.copyOf(topics);
    }

    public record TopicInfo(Integer id, String name) {
    }
}

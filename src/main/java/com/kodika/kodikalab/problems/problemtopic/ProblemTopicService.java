package com.kodika.kodikalab.problems.problemtopic;

import com.kodika.kodikalab.problems.problemtopic.dto.ProblemTopicData;
import java.util.List;
import java.util.Set;

public interface ProblemTopicService {
    List<ProblemTopicData> findTopicsByProblemIds(Set<Integer> problemIds);
}

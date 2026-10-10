package com.kodika.kodikalab.problems.problemtopic;

import com.kodika.kodikalab.problems.problemtopic.dto.ProblemTopicData;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProblemTopicServiceImpl implements ProblemTopicService {
    private final ProblemTopicRepository problemTopicRepository;

    public ProblemTopicServiceImpl(ProblemTopicRepository problemTopicRepository) {
        this.problemTopicRepository = problemTopicRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProblemTopicData> findTopicsByProblemIds(Set<Integer> problemIds) {
        return problemIds.isEmpty() ? List.of() : problemTopicRepository.findTopicsByProblemIds(problemIds);
    }
}

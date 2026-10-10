package com.kodika.kodikalab.problems.problemtopic;

import com.kodika.kodikalab.problems.problemtopic.dto.ProblemTopicData;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProblemTopicRepository extends JpaRepository<ProblemTopic, ProblemTopicId> {
    @Query("""
            select new com.kodika.kodikalab.problems.problemtopic.dto.ProblemTopicData(p.id, t.id, t.name)
            from ProblemTopic pt
            left join pt.problem p
            left join pt.topic t
            where p.id in :problemIds
            order by p.id, t.id
            """)
    List<ProblemTopicData> findTopicsByProblemIds(@Param("problemIds") Set<Integer> problemIds);
}

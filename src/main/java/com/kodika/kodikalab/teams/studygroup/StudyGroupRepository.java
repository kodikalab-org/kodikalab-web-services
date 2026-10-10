package com.kodika.kodikalab.teams.studygroup;

import com.kodika.kodikalab.teams.studygroup.dto.StudyGroupSummary;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudyGroupRepository extends JpaRepository<StudyGroup, Integer> {
    @Query("""
            select new com.kodika.kodikalab.teams.studygroup.dto.StudyGroupSummary(g.id, c.userId)
            from StudyGroup g left join g.coach c
            where g.id = :teamId
            """)
    Optional<StudyGroupSummary> findSummaryById(@Param("teamId") Integer teamId);
}

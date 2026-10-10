package com.kodika.kodikalab.teams.studygroup;

import com.kodika.kodikalab.teams.studygroup.dto.StudyGroupSummary;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudyGroupRepository extends JpaRepository<StudyGroup, Integer> {

    List<StudyGroup> findByStatusOrderByIdAsc(GroupStatus status);

    /** Grupos que creó el coach, de cualquier estado. */
    @Query("select g from StudyGroup g where g.coach.userId = :coachUserId order by g.id")
    List<StudyGroup> findByCoachUserId(@Param("coachUserId") Integer coachUserId);

    @Query("""
            select new com.kodika.kodikalab.teams.studygroup.dto.StudyGroupSummary(g.id, c.userId)
            from StudyGroup g left join g.coach c
            where g.id = :teamId
            """)
    Optional<StudyGroupSummary> findSummaryById(@Param("teamId") Integer teamId);
}

package com.kodika.kodikalab.competitions.competitionproblem;

import com.kodika.kodikalab.competitions.competitionproblem.dto.TeamAssignedProblem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompetitionProblemRepository extends JpaRepository<CompetitionProblem, Integer> {
    @Query("""
            select new com.kodika.kodikalab.competitions.competitionproblem.dto.TeamAssignedProblem(
                cp.id, c.id, g.id, p.id, c.status)
            from CompetitionProblem cp
            left join cp.competition c
            left join c.group g
            left join cp.problem p
            where g.id = :teamId
            order by cp.id
            """)
    List<TeamAssignedProblem> findAssignedProblemsByTeamId(@Param("teamId") Integer teamId);
}

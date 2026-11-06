package com.kodika.kodikalab.competitions.competitionproblem;

import com.kodika.kodikalab.competitions.competitionproblem.dto.TeamAssignedProblem;
import com.kodika.kodikalab.competitions.competitionproblem.dto.AssignedProblemResponse;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompetitionProblemRepository extends JpaRepository<CompetitionProblem, Integer> {
    @Query("""
            select new com.kodika.kodikalab.competitions.competitionproblem.dto.AssignedProblemResponse(
                cp.id, c.id, g.id, p.id, p.title, c.eventName, cp.letter, c.status)
            from CompetitionProblem cp join cp.competition c join c.group g join cp.problem p
            where g.id = :teamId
            order by c.startsAt desc, c.id desc, cp.letter, cp.id
            """)
    List<AssignedProblemResponse> findAvailableByTeamId(@Param("teamId") Integer teamId);

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

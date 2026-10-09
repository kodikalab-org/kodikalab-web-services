package com.kodika.kodikalab.competitions.problemresolution;

import com.kodika.kodikalab.competitions.problemresolution.dto.TeamResolutionData;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProblemResolutionRepository extends JpaRepository<ProblemResolution, Integer> {
    @Query("""
            select new com.kodika.kodikalab.competitions.problemresolution.dto.TeamResolutionData(
                r.id, m.id, mg.id, cp.id, c.id, cg.id, p.id, r.verdict)
            from ProblemResolution r
            left join r.membership m
            left join m.group mg
            left join r.competitionProblem cp
            left join cp.competition c
            left join c.group cg
            left join cp.problem p
            where mg.id = :teamId or cg.id = :teamId
            order by r.id
            """)
    List<TeamResolutionData> findResolutionsByTeamId(@Param("teamId") Integer teamId);
}

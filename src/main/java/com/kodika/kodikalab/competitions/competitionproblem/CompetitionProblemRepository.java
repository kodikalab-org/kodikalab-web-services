package com.kodika.kodikalab.competitions.competitionproblem;

import com.kodika.kodikalab.competitions.competitionproblem.dto.TeamAssignedProblem;
import java.util.List;
import java.util.Optional;
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

    // Query Method: problemas que forman parte de una competencia.
    List<CompetitionProblem> findByCompetition_IdOrderByLetterAsc(Integer competitionId);

    // JPQL: detalle con problema y competencia.
    @Query("select cp from CompetitionProblem cp join fetch cp.problem join fetch cp.competition where cp.id = :id")
    Optional<CompetitionProblem> findDetailById(@Param("id") Integer id);

    /** Asignaciones de todas las competencias del equipo, las más recientes primero y por letra dentro de cada una. */
    @Query("""
            select cp from CompetitionProblem cp
            join fetch cp.competition c
            where c.group.id = :teamId
            order by c.startsAt desc, cp.letter asc
            """)
    List<CompetitionProblem> findWithCompetitionByTeamId(@Param("teamId") Integer teamId);
}

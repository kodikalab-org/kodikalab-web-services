package com.kodika.kodikalab.competitions.competition;

import com.kodika.kodikalab.competitions.competition.dto.CompetitionListItem;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompetitionRepository extends JpaRepository<Competition, Integer> {
    boolean existsByGroupIdAndEventNameIgnoreCaseAndStartsAt(Integer groupId, String eventName,
                                                             OffsetDateTime startsAt);

    /** Bloquea la fila para serializar las asignaciones de problemas de una misma competencia. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Competition c where c.id = :id")
    Optional<Competition> findForUpdate(@Param("id") Integer id);

    /** Competencias del equipo con su cantidad de problemas asignados; la más reciente primero. */
    @Query("""
            select new com.kodika.kodikalab.competitions.competition.dto.CompetitionListItem(
                c.id, c.eventName, c.description, c.accessType, c.penaltyRule, c.durationMinutes,
                c.scoreboardFreezeMinutes, c.status, c.startsAt, c.endsAt,
                (select count(cp) from CompetitionProblem cp where cp.competition = c))
            from Competition c
            where c.group.id = :teamId
            order by c.startsAt desc, c.id desc
            """)
    List<CompetitionListItem> findListItemsByTeamId(@Param("teamId") Integer teamId);
}

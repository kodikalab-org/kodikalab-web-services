package com.kodika.kodikalab.competitions.officialresult;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OfficialResultRepository extends JpaRepository<OfficialResult, Integer> {
    Optional<OfficialResult> findByCompetitionId(Integer competitionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from OfficialResult r where r.competition.id = :competitionId")
    Optional<OfficialResult> findForUpdate(@Param("competitionId") Integer competitionId);

    @Query("""
            select r from OfficialResult r join fetch r.competition c
            where c.group.id = :teamId and r.status = :status
            order by c.endsAt desc, c.id desc
            """)
    List<OfficialResult> findHistory(@Param("teamId") Integer teamId,
                                   @Param("status") OfficialResultStatus status);
}

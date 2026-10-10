package com.kodika.kodikalab.competitions.competition;

import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
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
}

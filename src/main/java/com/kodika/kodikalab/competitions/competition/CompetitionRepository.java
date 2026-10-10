package com.kodika.kodikalab.competitions.competition;

import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompetitionRepository extends JpaRepository<Competition, Integer> {
    boolean existsByGroupIdAndEventNameIgnoreCaseAndStartsAt(Integer groupId, String eventName,
                                                             OffsetDateTime startsAt);
}

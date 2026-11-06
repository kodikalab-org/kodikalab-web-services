package com.kodika.kodikalab.competitions.competition;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CompetitionRepository extends JpaRepository<Competition, Integer> {
    List<Competition> findByGroupIdOrderByStartsAtDescIdDesc(Integer teamId);
}

package com.kodika.kodikalab.repository;

import com.kodika.kodikalab.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, Long> {
}

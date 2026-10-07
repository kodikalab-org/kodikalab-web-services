package com.kodika.kodikalab.profiles.coach;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoachProfileRepository extends JpaRepository<CoachProfile, Integer> {
    Optional<CoachProfile> findByUserId(Integer userId);
}

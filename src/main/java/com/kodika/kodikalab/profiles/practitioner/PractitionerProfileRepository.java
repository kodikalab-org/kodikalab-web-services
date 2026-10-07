package com.kodika.kodikalab.profiles.practitioner;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PractitionerProfileRepository extends JpaRepository<PractitionerProfile, Integer> {
    Optional<PractitionerProfile> findByUserId(Integer userId);

    boolean existsByStudentCodeAndUserIdNot(String studentCode, Integer userId);
}

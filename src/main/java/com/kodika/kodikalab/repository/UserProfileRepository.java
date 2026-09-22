package com.kodika.kodikalab.repository;

import com.kodika.kodikalab.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {
}

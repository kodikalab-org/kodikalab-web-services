package com.kodika.kodikalab.repository;

import com.kodika.kodikalab.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}

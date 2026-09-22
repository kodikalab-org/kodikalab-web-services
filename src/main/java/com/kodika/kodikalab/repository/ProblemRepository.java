package com.kodika.kodikalab.repository;

import com.kodika.kodikalab.entity.Problem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemRepository extends JpaRepository<Problem, Long> {
}

package com.kodika.kodikalab.repository;

import com.kodika.kodikalab.entity.ProblemSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemSubmissionRepository extends JpaRepository<ProblemSubmission, Long> {
}

package com.kodika.kodikalab.problems.problem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProblemRepository extends JpaRepository<Problem, Integer>, JpaSpecificationExecutor<Problem> {
    boolean existsBySourcePlatformAndSourceCodeIgnoreCase(SourcePlatform sourcePlatform, String sourceCode);

    boolean existsByUrlIgnoreCase(String url);
}

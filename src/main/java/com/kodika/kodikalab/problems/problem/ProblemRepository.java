package com.kodika.kodikalab.problems.problem;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProblemRepository extends JpaRepository<Problem, Integer>, JpaSpecificationExecutor<Problem> {
    boolean existsBySourcePlatformAndSourceCodeIgnoreCase(SourcePlatform sourcePlatform, String sourceCode);

    boolean existsByUrlIgnoreCase(String url);

    Optional<Problem> findFirstBySourcePlatformAndSourceCodeIgnoreCaseOrderByIdAsc(SourcePlatform sourcePlatform,
                                                                                    String sourceCode);

    Optional<Problem> findFirstByUrlIgnoreCaseOrderByIdAsc(String url);

    List<Problem> findAllBySourcePlatformAndSourceCodeIn(SourcePlatform sourcePlatform, Collection<String> sourceCodes);
}

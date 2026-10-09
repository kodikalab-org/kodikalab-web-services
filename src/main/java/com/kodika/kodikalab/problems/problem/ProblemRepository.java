package com.kodika.kodikalab.problems.problem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProblemRepository extends JpaRepository<Problem, Integer> {
    // Query Method: búsquedas que solo filtran columnas de problema.
    List<Problem> findByTitleContainingIgnoreCase(String title);
    List<Problem> findBySourceCodeContainingIgnoreCase(String sourceCode);
    List<Problem> findByDifficultyRating(String difficultyRating);

    // JPQL: detalle del problema y sus temas usando la entidad puente existente.
    @Query("select distinct p from Problem p, ProblemTopic pt where pt.problem = p and p.id = :problemId")
    List<Problem> findProblemWithTopics(@Param("problemId") Integer problemId);

    // SQL nativo: catálogo de problemas asociados a un tema.
    @Query(value = "select p.* from problema p join problema_tema pt on pt.problema_id = p.id where pt.tema_id = :topicId order by p.titulo", nativeQuery = true)
    List<Problem> listByTopicNative(@Param("topicId") Integer topicId);
}

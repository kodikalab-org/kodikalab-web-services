package com.kodika.kodikalab.problems.problemtopic;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProblemTopicRepository extends JpaRepository<ProblemTopic, ProblemTopicId> {
    // Query Method: relaciones de clasificación de un tema.
    List<ProblemTopic> findByTopicId(Integer topicId);

    // JPQL: problemas asociados a un tema específico.
    @Query("select pt from ProblemTopic pt join fetch pt.problem where pt.topic.id = :topicId")
    List<ProblemTopic> findProblemsByTopic(@Param("topicId") Integer topicId);

    // SQL nativo: catálogo clasificado por tema, útil para listados compactos.
    @Query(value = "select p.id, p.codigo_origen, p.titulo, p.dificultad_rating, t.nombre as tema " +
            "from problema p join problema_tema pt on pt.problema_id = p.id " +
            "join tema t on t.id = pt.tema_id where t.id = :topicId order by p.titulo", nativeQuery = true)
    List<Object[]> listProblemCatalogByTopicNative(@Param("topicId") Integer topicId);
}

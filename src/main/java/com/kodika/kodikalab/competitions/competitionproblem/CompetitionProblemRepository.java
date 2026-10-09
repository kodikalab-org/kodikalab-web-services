package com.kodika.kodikalab.competitions.competitionproblem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface CompetitionProblemRepository extends JpaRepository<CompetitionProblem, Integer> {
    // Query Method: problemas que forman parte de una competencia.
    List<CompetitionProblem> findByCompetition_IdOrderByLetterAsc(Integer competitionId);
    boolean existsByCompetition_IdAndProblem_Id(Integer competitionId, Integer problemId);

    // JPQL: detalle con problema y competencia.
    @Query("select cp from CompetitionProblem cp join fetch cp.problem join fetch cp.competition where cp.id = :id")
    java.util.Optional<CompetitionProblem> findDetailById(@Param("id") Integer id);

    // SQL nativo: problemas de las competencias del grupo, incluyendo datos de problema y estado de la competencia.
    @Query(value = "select cp.id as asignacion_id, cp.orden_letra, cp.puntaje, p.id as problema_id, p.titulo, p.url_problema, p.dificultad_rating, c.id as competencia_id, c.nombre_evento, c.estado as estado_competencia from competencia_problema cp join problema p on p.id = cp.problema_id join competencia c on c.id = cp.competencia_id where c.grupo_id = :groupId order by c.fecha_inicio desc, cp.orden_letra", nativeQuery = true)
    List<Object[]> listProblemsByGroupNative(@Param("groupId") Integer groupId);

    // @Modifying: actualizar los metadatos de una asignación existente.
    @Modifying
    @Transactional
    @Query("update CompetitionProblem cp set cp.score = :score, cp.letter = :letter where cp.id = :id")
    int updateAssignment(@Param("id") Integer id, @Param("letter") String letter, @Param("score") Integer score);
}

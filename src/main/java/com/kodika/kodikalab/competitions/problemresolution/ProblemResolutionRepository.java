package com.kodika.kodikalab.competitions.problemresolution;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.query.Procedure;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ProblemResolutionRepository extends JpaRepository<ProblemResolution, Integer> {
    // Query Methods: historial de intentos por integrante y problema.
    List<ProblemResolution> findByMembership_IdOrderBySubmittedAtDesc(Integer membershipId);
    List<ProblemResolution> findByMembership_IdAndCompetitionProblem_Problem_IdOrderBySubmittedAtDesc(Integer membershipId, Integer problemId);
    boolean existsByMembership_IdAndCompetitionProblem_Problem_IdAndVerdict(Integer membershipId, Integer problemId, Verdict verdict);

    // JPQL: listar problemas asignados/intentados por un integrante con estado y fecha.
    @Query("select pr from ProblemResolution pr join fetch pr.competitionProblem cp join fetch cp.problem where pr.membership.id = :membershipId order by pr.submittedAt desc")
    List<ProblemResolution> listAssignedProblemsByMembership(@Param("membershipId") Integer membershipId);

    // SQL nativo: resumen de estado por problema para un integrante.
    @Query(value = "select p.id as problema_id, p.titulo, cp.id as asignacion_id, max(r.fecha_envio) as ultimo_intento, case when bool_or(r.veredicto = 'ACCEPTED') then 'ACCEPTED' else (array_agg(r.veredicto order by r.fecha_envio desc))[1] end as estado from resolucion_problema r join competencia_problema cp on cp.id = r.competencia_problema_id join problema p on p.id = cp.problema_id where r.practicante_grupo_id = :membershipId group by p.id, p.titulo, cp.id order by max(r.fecha_envio) desc", nativeQuery = true)
    List<Object[]> listProblemStatusNative(@Param("membershipId") Integer membershipId);

    // Procedimientos almacenados para historial, estado y continuación de práctica.
    @Procedure(procedureName = "sp_listar_historial_integrante")
    Object listarHistorialProcedimiento(@Param("p_membership_id") Integer membershipId);

    @Procedure(procedureName = "sp_estado_problemas_integrante")
    Object estadoProblemasProcedimiento(@Param("p_membership_id") Integer membershipId);

    @Procedure(procedureName = "sp_continuar_practica")
    Object continuarPracticaProcedimiento(@Param("p_membership_id") Integer membershipId, @Param("p_problema_id") Integer problemId);

    // Registra un intento nuevo y devuelve el ID generado; no reemplaza el historial.
    @Procedure(procedureName = "sp_registrar_resolucion")
    Integer registrarResolucionProcedimiento(
            @Param("p_membership_id") Integer membershipId,
            @Param("p_competencia_problema_id") Integer competitionProblemId,
            @Param("p_veredicto") String verdict,
            @Param("p_lenguaje") String language,
            @Param("p_tiempo_ejecucion_ms") Integer executionTimeMs,
            @Param("p_memoria_usada_kb") Integer memoryUsedKb,
            @Param("p_url_evidencia") String evidenceUrl);

    // @Modifying: útil para continuar una práctica que quedó pendiente; el historial completo se conserva insertando nuevos intentos.
    @Modifying
    @Transactional
    @Query("update ProblemResolution pr set pr.verdict = :verdict where pr.id = :resolutionId and pr.verdict = com.kodika.kodikalab.competitions.problemresolution.Verdict.PENDIENTE")
    int updatePendingVerdict(@Param("resolutionId") Integer resolutionId, @Param("verdict") Verdict verdict);

    // SQL nativo: conteo de problemas distintos aceptados por tema para el integrante.
    @Query(value = "select t.id as tema_id, t.nombre, count(distinct cp.problema_id) filter (where r.veredicto = 'ACCEPTED') as resueltos from tema t join problema_tema pt on pt.tema_id = t.id join competencia_problema cp on cp.problema_id = pt.problema_id join competencia c on c.id = cp.competencia_id join resolucion_problema r on r.competencia_problema_id = cp.id and r.practicante_grupo_id = :membershipId where c.grupo_id = (select pg.grupo_id from practicante_grupo pg where pg.id = :membershipId) group by t.id, t.nombre order by t.nombre", nativeQuery = true)
    List<Object[]> countSolvedByTopicNative(@Param("membershipId") Integer membershipId);
}





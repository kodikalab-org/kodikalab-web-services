package com.kodika.kodikalab.analytics.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Progreso por tema del practicante autenticado en un equipo (US-10). Los problemas se cuentan una sola vez aunque se
 * asignen en varias competencias o tengan varios temas. Los temas salen con los más débiles primero.
 */
public record TopicProgressResponse(Integer teamId, Integer membershipId, Integer userId, int assignedProblems,
                                    int solvedProblems, int unclassifiedProblems, String reinforcementCriterion,
                                    List<TopicProgress> topics) {
    public TopicProgressResponse {
        topics = List.copyOf(topics);
    }

    /**
     * {@code SIN_ACTIVIDAD}: ningún intento en los problemas del tema. {@code EN_PROGRESO}: hay intentos pero faltan
     * problemas por resolver. {@code COMPLETADO}: todos los problemas asignados del tema están resueltos.
     */
    public enum TopicStatus {
        SIN_ACTIVIDAD, EN_PROGRESO, COMPLETADO
    }

    public record TopicProgress(Integer topicId, String topicName, int assignedProblems, int solvedProblems,
                                int unsolvedProblems, int pendingProblems, BigDecimal coveragePercentage,
                                TopicStatus status, boolean needsReinforcement) {
    }
}

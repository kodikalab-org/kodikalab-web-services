package com.kodika.kodikalab.agent.app.dto;

/**
 * Tarjeta de un problema recomendado (US-16), con el motivo de la selección. {@code source} es {@code CATALOG} o
 * {@code CODEFORCES}; estos últimos no tienen {@code problemId} hasta importarlos.
 */
public record ProblemInfo(Integer problemId, String title, String url, String topic, String difficulty, String reason,
                          String source) {
}

package com.kodika.kodikalab.agent.app.dto;

/** Tarjeta de un problema recomendado (US-16), con el motivo de la selección. */
public record ProblemInfo(Integer problemId, String title, String url, String topic, String difficulty, String reason) {
}

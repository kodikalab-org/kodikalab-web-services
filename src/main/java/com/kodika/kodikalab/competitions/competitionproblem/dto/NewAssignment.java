package com.kodika.kodikalab.competitions.competitionproblem.dto;

/** Problema a asignar a una competencia; la letra, el puntaje y el color ya vienen resueltos y validados. */
public record NewAssignment(Integer problemId, String letter, Integer score, String balloonColor) {
}

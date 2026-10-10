package com.kodika.kodikalab.competitions.competition.dto;

import com.kodika.kodikalab.competitions.competition.CompetitionStatus;

/** Datos mínimos de una competencia que otros módulos necesitan para autorizar y validar. */
public record CompetitionSummary(Integer id, Integer teamId, CompetitionStatus status) {
}

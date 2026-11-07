package com.kodika.kodikalab.competitions.competition.dto;

import com.kodika.kodikalab.competitions.competition.CompetitionStatus;
import com.kodika.kodikalab.competitions.competition.PenaltyRule;
import java.time.OffsetDateTime;

/** Datos de una competencia que otros módulos necesitan para mostrarla; nunca incluye la clave de acceso. */
public record CompetitionDetail(Integer id, Integer teamId, String eventName, CompetitionStatus status,
                                PenaltyRule penaltyRule, OffsetDateTime startsAt, OffsetDateTime endsAt) {
}

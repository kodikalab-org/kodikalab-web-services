package com.kodika.kodikalab.competitions.competition.dto;

import com.kodika.kodikalab.competitions.competition.CompetitionAccessType;
import com.kodika.kodikalab.competitions.competition.CompetitionStatus;
import com.kodika.kodikalab.competitions.competition.PenaltyRule;
import java.time.OffsetDateTime;

/** Competencia creada. Nunca incluye la clave de acceso. */
public record CompetitionResponse(Integer id, Integer teamId, String eventName, String description,
                                  CompetitionAccessType accessType, PenaltyRule penaltyRule, Integer durationMinutes,
                                  Integer scoreboardFreezeMinutes, CompetitionStatus status, OffsetDateTime startsAt,
                                  OffsetDateTime endsAt) {
}

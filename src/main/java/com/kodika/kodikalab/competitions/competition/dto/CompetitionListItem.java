package com.kodika.kodikalab.competitions.competition.dto;

import com.kodika.kodikalab.competitions.competition.CompetitionAccessType;
import com.kodika.kodikalab.competitions.competition.CompetitionStatus;
import com.kodika.kodikalab.competitions.competition.PenaltyRule;
import java.time.OffsetDateTime;

/** Competencia de un equipo en el listado, con la cantidad de problemas asignados. Nunca incluye la clave de acceso. */
public record CompetitionListItem(Integer id, String eventName, String description, CompetitionAccessType accessType,
                                  PenaltyRule penaltyRule, Integer durationMinutes, Integer scoreboardFreezeMinutes,
                                  CompetitionStatus status, OffsetDateTime startsAt, OffsetDateTime endsAt,
                                  Long problemsCount) {
}

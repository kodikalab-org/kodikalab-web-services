package com.kodika.kodikalab.competitions.competitionproblem.dto;

import com.kodika.kodikalab.competitions.competition.CompetitionAccessType;
import com.kodika.kodikalab.competitions.competition.CompetitionStatus;
import com.kodika.kodikalab.competitions.competition.PenaltyRule;
import java.time.OffsetDateTime;

/** Asignación con las condiciones de realización de su competencia. Nunca incluye la clave de acceso. */
public record AssignmentView(Integer competitionProblemId, Integer problemId, String letter, Integer score,
                             String balloonColor, OffsetDateTime assignedAt, Integer competitionId, Integer teamId,
                             String competitionName, CompetitionStatus competitionStatus, OffsetDateTime startsAt,
                             OffsetDateTime endsAt, Integer durationMinutes, PenaltyRule penaltyRule,
                             Integer scoreboardFreezeMinutes, CompetitionAccessType accessType) {
}

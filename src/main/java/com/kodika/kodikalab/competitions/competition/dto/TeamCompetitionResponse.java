package com.kodika.kodikalab.competitions.competition.dto;

import com.kodika.kodikalab.competitions.competition.CompetitionStatus;
import java.time.OffsetDateTime;

public record TeamCompetitionResponse(Integer competitionId, Integer teamId, String eventName,
                                      CompetitionStatus status, OffsetDateTime startsAt, OffsetDateTime endsAt) {
}

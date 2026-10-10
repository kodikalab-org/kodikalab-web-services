package com.kodika.kodikalab.competitions.officialresult.dto;

import com.kodika.kodikalab.competitions.officialresult.OfficialResultStatus;
import java.time.OffsetDateTime;

public record OfficialResultResponse(Integer id, Integer competitionId, Integer teamId, String eventName,
                                     OffsetDateTime competitionEndsAt, Integer finalPosition, Integer solvedProblems,
                                     OfficialResultStatus status, OffsetDateTime registeredAt,
                                     OffsetDateTime confirmedAt) {
}

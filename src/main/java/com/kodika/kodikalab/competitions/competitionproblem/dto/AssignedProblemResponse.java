package com.kodika.kodikalab.competitions.competitionproblem.dto;

import com.kodika.kodikalab.competitions.competition.CompetitionStatus;

public record AssignedProblemResponse(Integer competitionProblemId, Integer competitionId, Integer teamId,
                                      Integer problemId, String title, String eventName, String letter,
                                      CompetitionStatus status) {
}

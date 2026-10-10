package com.kodika.kodikalab.competitions.competitionproblem.dto;

import com.kodika.kodikalab.competitions.competition.CompetitionStatus;

public record TeamAssignedProblem(Integer competitionProblemId, Integer competitionId, Integer teamId,
                                  Integer problemId, CompetitionStatus status) {
}

package com.kodika.kodikalab.competitions.problemresolution.dto;

import com.kodika.kodikalab.competitions.problemresolution.Verdict;

public record TeamResolutionData(Integer resolutionId, Integer membershipId, Integer membershipTeamId,
                                 Integer competitionProblemId, Integer competitionId, Integer competitionTeamId,
                                 Integer problemId, Verdict verdict) {
}

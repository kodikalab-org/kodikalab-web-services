package com.kodika.kodikalab.competitions.competition.dto;

import java.util.List;

/** Competencias de un equipo, la más reciente primero. */
public record CompetitionListResponse(Integer teamId, int total, List<CompetitionListItem> items) {
    public CompetitionListResponse {
        items = List.copyOf(items);
    }
}

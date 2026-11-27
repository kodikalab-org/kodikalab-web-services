package com.kodika.kodikalab.agent.app.dto;

import java.util.List;

/**
 * Tarjeta de un grupo sugerido (US-15). {@code differences} explica qué criterios no cumple; vacía significa
 * coincidencia exacta.
 */
public record TeamMatchInfo(Integer groupId, String name, String expectedLevel, String sessionSchedule,
                            List<String> differences) {
    public TeamMatchInfo {
        differences = differences == null ? List.of() : List.copyOf(differences);
    }
}

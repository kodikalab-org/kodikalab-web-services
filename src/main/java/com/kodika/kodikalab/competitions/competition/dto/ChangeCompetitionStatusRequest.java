package com.kodika.kodikalab.competitions.competition.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;
import com.kodika.kodikalab.competitions.competition.CompetitionStatus;
import com.kodika.kodikalab.competitions.competition.CompetitionValidationException;
import java.util.Arrays;
import java.util.Map;

/**
 * Nuevo estado de una competencia. La lectura es estricta, igual que en {@link CreateCompetitionRequest}: el valor
 * debe ser el nombre exacto de un estado. Si la transición es válida lo decide el servicio.
 */
public record ChangeCompetitionStatusRequest(CompetitionStatus status) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ChangeCompetitionStatusRequest read(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new CompetitionValidationException(Map.of("body", "Debe enviar un objeto JSON"));
        }
        JsonNode value = body.get("status");
        if (value == null || value.isNull()) {
            return new ChangeCompetitionStatusRequest(null);
        }
        if (!value.isTextual()) {
            throw new CompetitionValidationException(Map.of("status", "Debe ser una cadena de texto"));
        }
        for (CompetitionStatus constant : CompetitionStatus.values()) {
            if (constant.name().equals(value.textValue())) {
                return new ChangeCompetitionStatusRequest(constant);
            }
        }
        throw new CompetitionValidationException(
                Map.of("status", "Valores permitidos: " + Arrays.toString(CompetitionStatus.values())));
    }
}

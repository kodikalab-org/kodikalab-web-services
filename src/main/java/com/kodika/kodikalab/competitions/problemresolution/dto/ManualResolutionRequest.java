package com.kodika.kodikalab.competitions.problemresolution.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;
import com.kodika.kodikalab.competitions.problemresolution.ResolutionValidationException;
import java.util.Map;

public record ManualResolutionRequest(String language, String evidenceUrl) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ManualResolutionRequest read(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new ResolutionValidationException(Map.of("body", "Debe enviar un objeto JSON"));
        }
        return new ManualResolutionRequest(string(body, "language"), string(body, "evidenceUrl"));
    }

    private static String string(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw new ResolutionValidationException(Map.of(field, "Debe ser una cadena de texto"));
        }
        return value.textValue();
    }
}

package com.kodika.kodikalab.competitions.officialresult.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;
import com.kodika.kodikalab.competitions.officialresult.OfficialResultValidationException;
import java.util.Map;

public record OfficialResultRequest(Integer finalPosition, Integer solvedProblems, boolean confirm) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static OfficialResultRequest read(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw invalid("body", "Debe enviar un objeto JSON");
        }
        JsonNode confirm = body.get("confirm");
        if (confirm != null && !confirm.isBoolean()) {
            throw invalid("confirm", "Debe ser un booleano");
        }
        return new OfficialResultRequest(integer(body, "finalPosition"), integer(body, "solvedProblems"),
                confirm != null && confirm.booleanValue());
    }

    private static Integer integer(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw invalid(field, "Debe ser un entero de 32 bits");
        }
        return value.intValue();
    }

    private static OfficialResultValidationException invalid(String field, String message) {
        return new OfficialResultValidationException(Map.of(field, message));
    }
}

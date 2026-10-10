package com.kodika.kodikalab.competitions.competition.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;
import com.kodika.kodikalab.competitions.competition.CompetitionAccessType;
import com.kodika.kodikalab.competitions.competition.CompetitionStatus;
import com.kodika.kodikalab.competitions.competition.CompetitionValidationException;
import com.kodika.kodikalab.competitions.competition.PenaltyRule;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Datos para crear una competencia. La lectura es estricta: cada campo debe tener el tipo esperado y no se
 * aceptan coerciones (una cadena no sustituye a un número). Se informan todos los campos con tipo inválido.
 */
public record CreateCompetitionRequest(Integer teamId, String eventName, String description,
                                       CompetitionAccessType accessType, String accessKey, PenaltyRule penaltyRule,
                                       Integer scoreboardFreezeMinutes, CompetitionStatus status,
                                       OffsetDateTime startsAt, OffsetDateTime endsAt) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static CreateCompetitionRequest read(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new CompetitionValidationException(Map.of("body", "Debe enviar un objeto JSON"));
        }
        Map<String, String> errors = new LinkedHashMap<>();
        CreateCompetitionRequest request = new CreateCompetitionRequest(
                integer(body, "teamId", errors), text(body, "eventName", errors), text(body, "description", errors),
                enumeration(body, "accessType", CompetitionAccessType.class, errors), text(body, "accessKey", errors),
                enumeration(body, "penaltyRule", PenaltyRule.class, errors),
                integer(body, "scoreboardFreezeMinutes", errors),
                enumeration(body, "status", CompetitionStatus.class, errors),
                date(body, "startsAt", errors), date(body, "endsAt", errors));
        if (!errors.isEmpty()) {
            throw new CompetitionValidationException(errors);
        }
        return request;
    }

    private static Integer integer(JsonNode body, String field, Map<String, String> errors) {
        JsonNode value = body.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            errors.put(field, "Debe ser un entero de 32 bits");
            return null;
        }
        return value.intValue();
    }

    private static String text(JsonNode body, String field, Map<String, String> errors) {
        JsonNode value = body.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            errors.put(field, "Debe ser una cadena de texto");
            return null;
        }
        return value.textValue();
    }

    private static <E extends Enum<E>> E enumeration(JsonNode body, String field, Class<E> type,
                                                      Map<String, String> errors) {
        String value = text(body, field, errors);
        if (value == null) {
            return null;
        }
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equals(value)) {
                return constant;
            }
        }
        errors.put(field, "Valores permitidos: " + Arrays.toString(type.getEnumConstants()));
        return null;
    }

    private static OffsetDateTime date(JsonNode body, String field, Map<String, String> errors) {
        String value = text(body, field, errors);
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException exception) {
            errors.put(field, "Debe ser una fecha y hora ISO-8601 con zona, por ejemplo 2026-10-20T14:00:00-05:00");
            return null;
        }
    }
}

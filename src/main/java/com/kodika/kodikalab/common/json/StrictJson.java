package com.kodika.kodikalab.common.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.kodika.kodikalab.common.exception.FieldValidationException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Map;

/**
 * Lectura estricta de JSON para los DTOs de entrada: cada campo debe tener exactamente el tipo esperado y no
 * se aceptan coerciones (una cadena no sustituye a un número). Los métodos no lanzan por un campo inválido:
 * registran el error en {@code errors} y devuelven {@code null}, para informar todos los campos juntos.
 * Los campos ausentes o {@code null} devuelven {@code null} sin error; la obligatoriedad se valida después.
 */
public final class StrictJson {
    private StrictJson() {
    }

    /** Exige que el cuerpo sea un objeto JSON. */
    public static JsonNode requireObject(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new FieldValidationException("Los datos enviados deben corregirse",
                    Map.of("body", "Debe enviar un objeto JSON"));
        }
        return body;
    }

    public static void failIfAny(Map<String, String> errors) {
        if (!errors.isEmpty()) {
            throw new FieldValidationException("Los datos enviados deben corregirse", errors);
        }
    }

    public static Integer integer(JsonNode node, String field, String errorKey, Map<String, String> errors) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            errors.put(errorKey, "Debe ser un entero de 32 bits");
            return null;
        }
        return value.intValue();
    }

    public static String text(JsonNode node, String field, String errorKey, Map<String, String> errors) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            errors.put(errorKey, "Debe ser una cadena de texto");
            return null;
        }
        return value.textValue();
    }

    public static <E extends Enum<E>> E enumeration(JsonNode node, String field, Class<E> type, String errorKey,
                                                     Map<String, String> errors) {
        String value = text(node, field, errorKey, errors);
        if (value == null) {
            return null;
        }
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equals(value)) {
                return constant;
            }
        }
        errors.put(errorKey, "Valores permitidos: " + Arrays.toString(type.getEnumConstants()));
        return null;
    }

    public static OffsetDateTime date(JsonNode node, String field, String errorKey, Map<String, String> errors) {
        String value = text(node, field, errorKey, errors);
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException exception) {
            errors.put(errorKey, "Debe ser una fecha y hora ISO-8601 con zona, por ejemplo 2026-10-20T14:00:00-05:00");
            return null;
        }
    }

    /** Devuelve el arreglo del campo, o {@code null} si falta; si existe pero no es un arreglo, registra el error. */
    public static JsonNode array(JsonNode node, String field, String errorKey, Map<String, String> errors) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isArray()) {
            errors.put(errorKey, "Debe ser un arreglo");
            return null;
        }
        return value;
    }
}

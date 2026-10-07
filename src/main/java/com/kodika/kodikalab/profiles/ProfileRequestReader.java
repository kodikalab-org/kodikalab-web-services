package com.kodika.kodikalab.profiles;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.kodika.kodikalab.profiles.practitioner.PractitionerLevel;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Convierte el body de {@code PUT /users/me} al DTO del rol de la sesión y lo valida.
 * Usa el ObjectMapper y el Validator de Spring para conservar la configuración global
 * (por ejemplo, ignorar propiedades desconocidas como {@code leetcodeHandle}).
 */
@Component
public class ProfileRequestReader {
    static final String INVALID_JSON = "La solicitud debe contener un JSON válido con los campos esperados";
    static final String INVALID_LEVEL = "El nivel competitivo debe ser PRINCIPIANTE, INTERMEDIO o AVANZADO";
    private static final String INVALID_PROFILE = "Datos de perfil inválidos";

    private final ObjectMapper objectMapper;
    private final Validator validator;

    public ProfileRequestReader(ObjectMapper objectMapper, Validator validator) {
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    public <T extends Record> T read(JsonNode body, Class<T> type) {
        if (body == null || !body.isObject()) {
            throw new ProfileValidationException(INVALID_JSON, Map.of());
        }
        T request = convert(body, type);
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            Map<String, String> errors = errorsInDeclarationOrder(type, violations);
            throw new ProfileValidationException(
                    errors.values().stream().findFirst().orElse(INVALID_PROFILE), errors);
        }
        return request;
    }

    private <T> T convert(JsonNode body, Class<T> type) {
        try {
            return objectMapper.treeToValue(body, type);
        } catch (InvalidFormatException exception) {
            if (exception.getTargetType() == PractitionerLevel.class) {
                throw new ProfileValidationException(INVALID_LEVEL, Map.of());
            }
            throw new ProfileValidationException(INVALID_JSON, Map.of());
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new ProfileValidationException(INVALID_JSON, Map.of());
        }
    }

    private <T> Map<String, String> errorsInDeclarationOrder(Class<T> type, Set<ConstraintViolation<T>> violations) {
        List<String> fieldOrder = Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
        Map<String, String> errors = new LinkedHashMap<>();
        violations.stream()
                .sorted(Comparator.comparingInt((ConstraintViolation<T> violation) ->
                                fieldOrder.indexOf(violation.getPropertyPath().toString()))
                        .thenComparing(ConstraintViolation::getMessage))
                .forEach(violation -> errors.putIfAbsent(
                        violation.getPropertyPath().toString(), violation.getMessage()));
        return errors;
    }
}

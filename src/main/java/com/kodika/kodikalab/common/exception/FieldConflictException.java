package com.kodika.kodikalab.common.exception;

import java.util.Map;

/**
 * Conflicto con datos existentes (HTTP 409) que indica qué campos lo causan, por ejemplo un problema que ya
 * está asignado. Las claves de {@code errors} siguen la misma convención que {@link FieldValidationException}.
 */
public class FieldConflictException extends RuntimeException {
    private final transient Map<String, String> errors;

    public FieldConflictException(String message, Map<String, String> errors) {
        super(message);
        this.errors = Map.copyOf(errors);
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}

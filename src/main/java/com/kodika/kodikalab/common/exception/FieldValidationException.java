package com.kodika.kodikalab.common.exception;

import java.util.Map;

/**
 * Datos inválidos con el detalle por campo (HTTP 400). Las claves de {@code errors} son rutas de campo
 * como {@code title} o {@code problems[0].problemId}.
 */
public class FieldValidationException extends RuntimeException {
    private final transient Map<String, String> errors;

    public FieldValidationException(String message, Map<String, String> errors) {
        super(message);
        this.errors = Map.copyOf(errors);
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}

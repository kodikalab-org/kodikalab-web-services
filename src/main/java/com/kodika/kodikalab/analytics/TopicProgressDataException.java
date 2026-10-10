package com.kodika.kodikalab.analytics;

import java.util.Map;

/** Datos faltantes o inconsistentes: no se calcula el progreso por tema para no mostrar indicadores incorrectos. */
public class TopicProgressDataException extends RuntimeException {
    private final transient Map<String, String> errors;

    public TopicProgressDataException(String message, Map<String, String> errors) {
        super(message);
        this.errors = Map.copyOf(errors);
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}

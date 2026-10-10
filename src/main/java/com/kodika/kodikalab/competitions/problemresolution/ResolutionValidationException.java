package com.kodika.kodikalab.competitions.problemresolution;

import java.util.Map;

public class ResolutionValidationException extends RuntimeException {
    private final transient Map<String, String> errors;

    public ResolutionValidationException(Map<String, String> errors) {
        super("Los datos de la resolución deben corregirse");
        this.errors = Map.copyOf(errors);
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}

package com.kodika.kodikalab.competitions.competition;

import java.util.Map;

public class CompetitionValidationException extends RuntimeException {
    private final transient Map<String, String> errors;

    public CompetitionValidationException(Map<String, String> errors) {
        super("Los datos de la competencia deben corregirse");
        this.errors = Map.copyOf(errors);
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}

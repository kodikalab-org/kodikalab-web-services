package com.kodika.kodikalab.competitions.officialresult;

import java.util.Map;

public class OfficialResultValidationException extends RuntimeException {
    private final transient Map<String, String> errors;

    public OfficialResultValidationException(Map<String, String> errors) {
        super("Los datos del resultado oficial deben corregirse");
        this.errors = Map.copyOf(errors);
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}

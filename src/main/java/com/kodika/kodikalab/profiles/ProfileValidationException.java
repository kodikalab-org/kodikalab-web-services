package com.kodika.kodikalab.profiles;

import java.util.Map;

public class ProfileValidationException extends RuntimeException {
    private final transient Map<String, String> errors;

    public ProfileValidationException(String message, Map<String, String> errors) {
        super(message);
        this.errors = errors;
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}

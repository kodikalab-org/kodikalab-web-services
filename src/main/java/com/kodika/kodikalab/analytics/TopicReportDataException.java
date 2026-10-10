package com.kodika.kodikalab.analytics;

import java.util.Map;

public class TopicReportDataException extends RuntimeException {
    private final transient Map<String, String> errors;

    public TopicReportDataException(String message, Map<String, String> errors) {
        super(message);
        this.errors = Map.copyOf(errors);
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}

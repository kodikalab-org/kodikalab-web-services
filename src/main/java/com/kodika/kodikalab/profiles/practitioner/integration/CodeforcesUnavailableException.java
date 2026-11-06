package com.kodika.kodikalab.profiles.practitioner.integration;

public class CodeforcesUnavailableException extends RuntimeException {
    public CodeforcesUnavailableException() {
        super("Codeforces no está disponible temporalmente. Inténtelo de nuevo más tarde.");
    }
}

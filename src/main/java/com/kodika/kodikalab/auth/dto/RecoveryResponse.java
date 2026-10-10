package com.kodika.kodikalab.auth.dto;

/** Respuesta con el código de recuperación vigente; el cliente debe mostrarlo al titular para que lo guarde. */
public record RecoveryResponse(String message, String recoveryCode) {

    @Override
    public String toString() {
        return "RecoveryResponse[message=" + message + ", recoveryCode=REDACTED]";
    }
}

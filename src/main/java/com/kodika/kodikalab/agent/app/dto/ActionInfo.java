package com.kodika.kodikalab.agent.app.dto;

import java.time.OffsetDateTime;

/**
 * Acción de escritura que espera confirmación (US de comandos). No se ejecuta hasta llamar a
 * {@code /agent/confirm} con {@code actionId}.
 */
public record ActionInfo(String actionId, String type, String summary, OffsetDateTime expiresAt) {
}

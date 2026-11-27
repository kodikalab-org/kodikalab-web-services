package com.kodika.kodikalab.agent.core.conversation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Cuerpo de {@code POST /agent/chat}. Contrato: {@code docs/agent/contract.md}. */
public record ChatRequest(
        @NotBlank(message = "El mensaje es obligatorio")
        @Size(max = 1000, message = "El mensaje debe tener como máximo 1000 caracteres") String message,
        @Pattern(regexp = "[0-9a-fA-F]{8}-([0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}",
                message = "El identificador de la conversación no es válido") String conversationId,
        @Positive(message = "El equipo debe ser un entero positivo") Integer teamId) {

    public ChatRequest {
        message = message == null ? null : message.strip();
    }

    @Override
    public String toString() {
        // The message is private: it must not end up in logs.
        return "ChatRequest[conversationId=" + conversationId + ", teamId=" + teamId + "]";
    }
}

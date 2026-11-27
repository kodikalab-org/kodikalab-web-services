package com.kodika.kodikalab.agent.app.tools;

import java.util.List;

/** Lo que una tool devuelve al modelo: el resultado y lo justo para redactar la respuesta, sin cifras. */
public record ToolOutcome(Status status, String message, List<String> details) {

    public enum Status {
        OK,
        /** Falta saber de qué equipo se trata: el modelo debe preguntarlo. */
        NEEDS_TEAM,
        /** No hay datos suficientes para concluir algo. */
        INSUFFICIENT_DATA,
        /** El usuario no puede realizar la consulta. */
        DENIED
    }

    public static ToolOutcome of(Status status, String message) {
        return new ToolOutcome(status, message, List.of());
    }
}

package com.kodika.kodikalab.agent.core;

import com.kodika.kodikalab.agent.app.dto.AgentResponse;
import com.kodika.kodikalab.agent.core.conversation.ChatRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agent")
@Tag(name = "Asistente de IA", description = "Conversación con el agente de KodikaLab.")
public class AgentController {
    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @Operation(summary = "Enviar un mensaje al asistente",
            description = "El router elige el agente; el código valida el rol. Las cifras y tarjetas de la respuesta "
                    + "salen de los datos de la plataforma. Responde 503 si la IA está desactivada o no disponible.")
    @PostMapping("/chat")
    public AgentResponse chat(@Valid @RequestBody ChatRequest request) {
        return agentService.chat(request);
    }
}

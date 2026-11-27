package com.kodika.kodikalab.agent.core;

import com.kodika.kodikalab.agent.app.dto.AgentResponse;
import com.kodika.kodikalab.agent.app.dto.ResponseData;
import com.kodika.kodikalab.agent.core.conversation.ChatRequest;
import com.kodika.kodikalab.agent.core.routing.RoutingWorkflow;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.ServiceUnavailableException;
import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserStatus;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.model.tool.ToolCallLimitExceededException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * Orquesta un mensaje: comprueba la cuenta y el límite, pide al router el agente, valida el rol en código y ejecuta el
 * agente con sus tools. Se ejecuta en el hilo de la petición, así que las tools ven la sesión actual.
 * <p>
 * Todavía no guarda memoria (F2.3): cada mensaje se atiende por separado.
 */
@Service
public class AgentService {
    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    private static final String NONE = "none";
    private static final String NO_ANSWER = "No pude redactar una respuesta. Intenta reformular tu pregunta.";

    private final CurrentUserResolver currentUserResolver;
    private final RoutingWorkflow routing;
    private final List<AgentDefinition> agents;
    private final ObjectProvider<ChatModel> chatModels;
    private final ObjectProvider<ChatClient.Builder> chatClients;
    private final AgentRateLimiter rateLimiter;

    public AgentService(CurrentUserResolver currentUserResolver, RoutingWorkflow routing, List<AgentDefinition> agents,
                        ObjectProvider<ChatModel> chatModels, ObjectProvider<ChatClient.Builder> chatClients,
                        AgentRateLimiter rateLimiter) {
        this.currentUserResolver = currentUserResolver;
        this.routing = routing;
        this.agents = List.copyOf(agents);
        this.chatModels = chatModels;
        this.chatClients = chatClients;
        this.rateLimiter = rateLimiter;
    }

    public AgentResponse chat(ChatRequest request) {
        User user = currentUserResolver.currentUser();
        if (user.getStatus() != UserStatus.ACTIVO) {
            throw new ForbiddenException("La cuenta no está activa");
        }
        ChatModel model = chatModels.getIfAvailable();
        if (model == null) {
            throw new ServiceUnavailableException("El asistente de IA está desactivado");
        }
        rateLimiter.acquire(user.getId());
        String conversationId = request.conversationId() == null ? UUID.randomUUID().toString()
                : request.conversationId();
        ResponseData data = new ResponseData();

        AgentDefinition agent = fromProvider(() -> routing.route(model, request.message(), agents).orElse(null));
        if (agent == null) {
            return data.toResponse(conversationId, NONE, "No puedo ayudarte con eso. Puedo ayudarte con tus equipos "
                    + "y con la práctica de programación competitiva.");
        }
        if (!agent.isAllowedFor(user.getRole())) {
            return data.toResponse(conversationId, agent.name(), "Esa opción no está disponible para tu rol");
        }
        if (agent.tools().isEmpty()) {
            return data.toResponse(conversationId, agent.name(), "Esa función todavía no está disponible");
        }

        Map<String, Object> toolContext = new HashMap<>();
        toolContext.put(ResponseData.CONTEXT_KEY, data);
        if (request.teamId() != null) {
            toolContext.put(ResponseData.TEAM_ID_KEY, request.teamId());
        }
        String system = systemPrompt(agent, request.teamId() != null);
        // The auto-configured builder applies the tool limits and error policy; ChatClient.create would skip them.
        ChatResponse answer = fromProvider(() -> chatClients.getObject().build().prompt()
                .system(system)
                .user(request.message())
                .tools(agent.tools().toArray())
                .toolContext(toolContext)
                .call()
                .chatResponse());
        return data.toResponse(conversationId, agent.name(), text(answer));
    }

    private static String text(ChatResponse response) {
        Generation result = response == null ? null : response.getResult();
        if (result == null) {
            return NO_ANSWER;
        }
        // When the tool limit is hit, the library answers with its own internal message.
        if (ToolCallLimitExceededException.FINISH_REASON.equals(result.getMetadata().getFinishReason())) {
            return "La consulta requirió demasiados pasos. Intenta con una pregunta más concreta.";
        }
        String text = result.getOutput().getText();
        return text == null || text.isBlank() ? NO_ANSWER : text.strip();
    }

    /** Provider details may contain request data or keys, so only the exception type reaches the log. */
    private static <T> T fromProvider(Supplier<T> call) {
        try {
            return call.get();
        } catch (RuntimeException exception) {
            log.error("The AI request failed: {}", exception.getClass().getName());
            log.debug("AI failure detail", exception);
            throw new ServiceUnavailableException("El asistente de IA no está disponible. Intenta más tarde");
        }
    }

    private static String systemPrompt(AgentDefinition agent, boolean teamOpen) {
        String language = "en".equals(LocaleContextHolder.getLocale().getLanguage())
                ? "Always answer in English." : "Responde siempre en español latinoamericano.";
        // The model cannot see the tool context, so it is told whether a team is already selected.
        String team = teamOpen ? "Hay un equipo abierto en la aplicación; las tools lo usan automáticamente."
                : "No hay ningún equipo abierto en la aplicación.";
        return prompt("common") + "\n\n" + prompt(agent.name()) + "\n\n" + team + "\n" + language;
    }

    private static String prompt(String name) {
        try {
            return new ClassPathResource("prompts/" + name + ".md").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Missing prompt " + name, exception);
        }
    }
}

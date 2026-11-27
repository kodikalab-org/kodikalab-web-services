package com.kodika.kodikalab.agent.core;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kodika.kodikalab.agent.app.dto.AgentResponse;
import com.kodika.kodikalab.agent.core.conversation.ChatRequest;
import com.kodika.kodikalab.common.exception.ServiceUnavailableException;
import com.kodika.kodikalab.common.exception.TooManyRequestsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AgentControllerTests {
    private final AgentService service = mock(AgentService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new AgentController(service))
            .setControllerAdvice(new AgentExceptionHandler()).build();

    @Test
    void returnsTheAgentResponseAndTrimsTheMessage() throws Exception {
        when(service.chat(any())).thenReturn(new AgentResponse("c-1", "insights", "Hola", null, null, null, null));

        send("{\"message\":\"  hola  \",\"teamId\":7}").andExpect(status().isOk())
                .andExpect(jsonPath("$.agent").value("insights"))
                .andExpect(jsonPath("$.problems").doesNotExist());

        verify(service).chat(new ChatRequest("hola", null, 7));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"message\":\"   \"}", "{\"message\":\"x\",\"teamId\":0}",
            "{\"message\":\"x\",\"conversationId\":\"not-a-uuid\"}", "not json"})
    void invalidBodiesAreRejectedBeforeTheAgent(String body) throws Exception {
        send(body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.errors").exists());

        verifyNoInteractions(service);
    }

    @Test
    void aMessageLongerThanOneThousandCharactersIsRejected() throws Exception {
        send("{\"message\":\"" + "a".repeat(1001) + "\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.message").isNotEmpty());
    }

    @Test
    void limitsAndProviderFailuresKeepTheCommonErrorBody() throws Exception {
        when(service.chat(any())).thenThrow(new TooManyRequestsException("Espera"))
                .thenThrow(new ServiceUnavailableException("No disponible"));

        send("{\"message\":\"x\"}").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Espera"));
        send("{\"message\":\"x\"}").andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("No disponible"));
    }

    private org.springframework.test.web.servlet.ResultActions send(String body) throws Exception {
        return mvc.perform(post("/agent/chat").contentType(MediaType.APPLICATION_JSON).content(body));
    }
}

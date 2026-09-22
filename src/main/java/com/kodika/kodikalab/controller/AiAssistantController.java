package com.kodika.kodikalab.controller;

import com.kodika.kodikalab.dto.AiResponse;
import com.kodika.kodikalab.dto.PromptRequest;
import com.kodika.kodikalab.service.AiAssistantService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/assistant")
public class AiAssistantController {

    private final AiAssistantService aiAssistantService;

    public AiAssistantController(AiAssistantService aiAssistantService) {
        this.aiAssistantService = aiAssistantService;
    }

    @PostMapping("/query")
    public ResponseEntity<AiResponse> processPrompt(@RequestBody PromptRequest request) {
        return ResponseEntity.ok(aiAssistantService.processPrompt(request));
    }
}

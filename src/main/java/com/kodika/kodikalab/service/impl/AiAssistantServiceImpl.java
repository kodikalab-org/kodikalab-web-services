package com.kodika.kodikalab.service.impl;

import com.kodika.kodikalab.dto.AiResponse;
import com.kodika.kodikalab.dto.PromptRequest;
import com.kodika.kodikalab.dto.ProblemResponse;
import java.util.List;
import com.kodika.kodikalab.service.AiAssistantService;
import org.springframework.stereotype.Service;

@Service
public class AiAssistantServiceImpl implements AiAssistantService {

    @Override
    public AiResponse processPrompt(PromptRequest request) {
        return null;
    }
    @Override
    public List<ProblemResponse> recommendProblems(PromptRequest request) {
        return null;
    }
}

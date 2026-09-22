package com.kodika.kodikalab.service;

import com.kodika.kodikalab.dto.AiResponse;
import com.kodika.kodikalab.dto.PromptRequest;
import com.kodika.kodikalab.dto.ProblemResponse;
import java.util.List;

public interface AiAssistantService {

    AiResponse processPrompt(PromptRequest request);

    List<ProblemResponse> recommendProblems(PromptRequest request);
}

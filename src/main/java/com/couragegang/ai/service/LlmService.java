package com.couragegang.ai.service;

import com.couragegang.ai.config.AiProperties;
import com.couragegang.ai.integration.DeepSeekClient;
import com.couragegang.ai.integration.DeepSeekClient.DeepSeekException;
import jakarta.inject.Singleton;

@Singleton
public final class LlmService {

    private final AiProperties properties;
    private final DeepSeekClient deepSeek;

    public LlmService(AiProperties properties, DeepSeekClient deepSeek) {
        this.properties = properties;
        this.deepSeek = deepSeek;
    }

    public LlmReply complete(String userMessage, String workspaceLabel) {
        if (isDeepSeek()) {
            return completeWithDeepSeek(userMessage);
        }
        return stubReply(userMessage, workspaceLabel);
    }

    private boolean isDeepSeek() {
        return "deepseek".equalsIgnoreCase(properties.getLlmProvider());
    }

    private LlmReply completeWithDeepSeek(String userMessage) {
        try {
            var reply = deepSeek.complete(userMessage);
            return new LlmReply(reply, "completed");
        } catch (DeepSeekException e) {
            return new LlmReply("LLM error: " + e.getMessage(), "error");
        }
    }

    private static LlmReply stubReply(String userMessage, String workspaceLabel) {
        return new LlmReply(
                "Заглушка ai-runtime (workspace=" + workspaceLabel + "): " + userMessage, "stub");
    }

    public record LlmReply(String reply, String status) {}
}

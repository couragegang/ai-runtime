package com.couragegang.ai.service;

import com.couragegang.ai.config.AiProperties;
import com.couragegang.ai.integration.DeepSeekClient;
import com.couragegang.ai.integration.DeepSeekClient.DeepSeekException;
import jakarta.inject.Singleton;
import java.util.List;

@Singleton
public final class LlmService {

    private static final String TITLE_SYSTEM_PROMPT =
            """
            You generate short chat titles. Given the user's first message, reply with a concise \
            conversation title (at most 8 words) in the same language as the message. \
            Output only the title: no quotes, no trailing punctuation, no explanation.""";

    private final AiProperties properties;
    private final DeepSeekClient deepSeek;

    public LlmService(AiProperties properties, DeepSeekClient deepSeek) {
        this.properties = properties;
        this.deepSeek = deepSeek;
    }

    public String generateConversationTitle(String firstMessage) {
        if (firstMessage == null || firstMessage.isBlank()) {
            return "Новый чат";
        }
        if (isDeepSeek()) {
            try {
                var raw = deepSeek.completeWithSystem(TITLE_SYSTEM_PROMPT, firstMessage.strip());
                return sanitizeTitle(raw);
            } catch (DeepSeekException e) {
                return fallbackTitle(firstMessage);
            }
        }
        return fallbackTitle(firstMessage);
    }

    public LlmReply complete(String userMessage, String workspaceLabel) {
        return complete(userMessage, workspaceLabel, null);
    }

    public LlmReply complete(String userMessage, String workspaceLabel, String extraSystemContext) {
        if (isDeepSeek()) {
            return completeWithDeepSeek(userMessage, extraSystemContext);
        }
        return stubReply(userMessage, workspaceLabel, extraSystemContext);
    }

    public LlmReply completeWithHistory(
            List<ChatTurn> history, String workspaceLabel, String extraSystemContext) {
        if (history == null || history.isEmpty()) {
            return complete("", workspaceLabel, extraSystemContext);
        }
        if (isDeepSeek()) {
            try {
                var reply = deepSeek.completeWithHistory(history, extraSystemContext);
                return new LlmReply(reply, "completed");
            } catch (DeepSeekClient.DeepSeekException e) {
                return new LlmReply("LLM error: " + e.getMessage(), "error");
            }
        }
        var lastUser =
                history.reversed().stream()
                        .filter(t -> "user".equals(t.role()))
                        .map(ChatTurn::content)
                        .findFirst()
                        .orElse("");
        return stubReply(lastUser, workspaceLabel, extraSystemContext);
    }

    private boolean isDeepSeek() {
        return "deepseek".equalsIgnoreCase(properties.getLlmProvider());
    }

    private LlmReply completeWithDeepSeek(String userMessage, String extraSystemContext) {
        try {
            var reply = deepSeek.complete(userMessage, extraSystemContext);
            return new LlmReply(reply, "completed");
        } catch (DeepSeekException e) {
            return new LlmReply("LLM error: " + e.getMessage(), "error");
        }
    }

    private static LlmReply stubReply(String userMessage, String workspaceLabel, String extraSystemContext) {
        var suffix = extraSystemContext != null && !extraSystemContext.isBlank()
                ? " [mcp: " + extraSystemContext + "]"
                : "";
        return new LlmReply(
                "Заглушка ai-runtime (workspace=" + workspaceLabel + "): " + userMessage + suffix, "stub");
    }

    private static String fallbackTitle(String message) {
        var trimmed = message.strip();
        if (trimmed.length() <= 80) {
            return trimmed.isEmpty() ? "Новый чат" : trimmed;
        }
        return trimmed.substring(0, 77) + "...";
    }

    private static String sanitizeTitle(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Новый чат";
        }
        var line = raw.strip().lines().findFirst().orElse("").trim();
        line = line.replaceAll("^[\"'«»]+|[\"'«»]+$", "").trim();
        if (line.length() > 80) {
            line = line.substring(0, 77) + "...";
        }
        return line.isEmpty() ? "Новый чат" : line;
    }

    public record LlmReply(String reply, String status) {}
}

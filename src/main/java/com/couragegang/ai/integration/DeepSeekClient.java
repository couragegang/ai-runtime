package com.couragegang.ai.integration;

import com.couragegang.ai.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.inject.Singleton;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public final class DeepSeekClient {

    private static final Logger LOG = LoggerFactory.getLogger(DeepSeekClient.class);

    private final AiProperties.DeepSeek config;
    private final HttpClient http;
    private final ObjectMapper json;
    private final String chatCompletionsUrl;

    public DeepSeekClient(AiProperties properties) {
        this.config = properties.getDeepseek();
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.json = new ObjectMapper();
        var base = config.getBaseUrl().endsWith("/") ? config.getBaseUrl().substring(0, config.getBaseUrl().length() - 1) : config.getBaseUrl();
        this.chatCompletionsUrl = base + "/chat/completions";
    }

    public String complete(String userMessage) {
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new DeepSeekException("DEEPSEEK_API_KEY is not configured");
        }
        try {
            var body = buildRequestBody(userMessage);
            var request =
                    HttpRequest.newBuilder(URI.create(chatCompletionsUrl))
                            .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + config.getApiKey().trim())
                            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                            .build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOG.warn("deepseek http {}: {}", response.statusCode(), truncate(response.body()));
                throw new DeepSeekException("DeepSeek API returned HTTP " + response.statusCode());
            }
            return extractAssistantText(json.readTree(response.body()));
        } catch (DeepSeekException e) {
            throw e;
        } catch (Exception e) {
            LOG.warn("deepseek request failed: {}", e.toString());
            throw new DeepSeekException("DeepSeek request failed: " + e.getMessage(), e);
        }
    }

    private String buildRequestBody(String userMessage) throws Exception {
        ObjectNode root = json.createObjectNode();
        root.put("model", config.getModel());
        root.put("stream", false);
        ObjectNode thinking = root.putObject("thinking");
        thinking.put("type", config.getThinkingType());
        ArrayNode messages = root.putArray("messages");
        messages.addObject().put("role", "system").put("content", config.getSystemPrompt());
        messages.addObject().put("role", "user").put("content", userMessage);
        return json.writeValueAsString(root);
    }

    private static String extractAssistantText(JsonNode root) {
        var message = root.path("choices").path(0).path("message");
        var content = textOrNull(message.path("content"));
        if (content != null && !content.isBlank()) {
            return content.trim();
        }
        var reasoning = textOrNull(message.path("reasoning_content"));
        if (reasoning != null && !reasoning.isBlank()) {
            return reasoning.trim();
        }
        throw new DeepSeekException("DeepSeek response had no assistant content");
    }

    private static String textOrNull(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.asText(null);
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 500 ? s.substring(0, 500) + "..." : s;
    }

    public static final class DeepSeekException extends RuntimeException {
        public DeepSeekException(String message) {
            super(message);
        }

        public DeepSeekException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

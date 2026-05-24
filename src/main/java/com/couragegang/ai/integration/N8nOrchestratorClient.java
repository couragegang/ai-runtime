package com.couragegang.ai.integration;

import com.couragegang.ai.api.dto.OrchestratorDtos.OrchestratorStartRequest;
import com.couragegang.ai.metrics.OutboundHttpMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Value;
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
public final class N8nOrchestratorClient {

    private static final Logger LOG = LoggerFactory.getLogger(N8nOrchestratorClient.class);

    private final boolean enabled;
    private final String webhookUrl;
    private final HttpClient http;
    private final OutboundHttpMetrics metrics;
    private final ObjectMapper json;

    public N8nOrchestratorClient(
            @Value("${ai.n8n.enabled:false}") boolean enabled,
            @Value("${ai.n8n.webhook-url:http://localhost:5678/webhook/chat-orchestrator}") String webhookUrl,
            OutboundHttpMetrics metrics) {
        this.enabled = enabled;
        this.webhookUrl = webhookUrl;
        this.metrics = metrics;
        this.json = new ObjectMapper();
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        LOG.info("n8n orchestrator client enabled={} webhookUrl={}", enabled, webhookUrl);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean triggerRun(OrchestratorStartRequest payload) {
        if (!enabled) {
            LOG.debug("n8n webhook skipped: ai.n8n.enabled=false");
            return false;
        }
        try {
            var body = json.writeValueAsString(payload);
            var request =
                    HttpRequest.newBuilder(URI.create(webhookUrl))
                            .timeout(Duration.ofSeconds(10))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                            .build();
            // Не ждём конца run: webhook onReceived отвечает сразу; результат — callback в ai-runtime.
            http.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .whenComplete(
                            (response, err) -> {
                                if (err != null) {
                                    LOG.warn("n8n webhook async error: {}", err.toString());
                                    return;
                                }
                                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                                    LOG.debug("n8n webhook accepted status={}", response.statusCode());
                                } else {
                                    LOG.warn("n8n webhook failed: status={}", response.statusCode());
                                }
                            });
            return true;
        } catch (Exception e) {
            LOG.warn("n8n webhook error: {}", e.toString());
        }
        return false;
    }
}

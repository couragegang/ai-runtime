package com.couragegang.ai.integration;

import com.couragegang.ai.api.dto.OrchestratorDtos.OrchestratorStartRequest;
import com.couragegang.ai.metrics.OutboundHttpMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
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
        this.http =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(15))
                        .version(HttpClient.Version.HTTP_1_1)
                        .build();
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
                            // onReceived отвечает сразу; 30s — запас на cold start n8n после entrypoint import
                            .timeout(Duration.ofSeconds(30))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                            .build();

            try {
                return sendWebhook(request);
            } catch (Exception firstError) {
                if (!shouldRetry(firstError)) {
                    throw firstError;
                }
                LOG.warn("n8n webhook transient error (attempt=1): {}. Retrying once...", firstError.toString());
                Thread.sleep(250);
                return sendWebhook(request);
            }
        } catch (Exception e) {
            LOG.warn("n8n webhook error: {}", e.toString());
        }
        return false;
    }

    private boolean sendWebhook(HttpRequest request) throws java.io.IOException, InterruptedException {
        var response = http.send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            LOG.debug("n8n webhook accepted status={}", response.statusCode());
            return true;
        }
        LOG.warn("n8n webhook failed: status={} url={}", response.statusCode(), webhookUrl);
        return false;
    }

    private static boolean shouldRetry(Exception e) {
        if (e instanceof HttpTimeoutException || e instanceof ConnectException) {
            return true;
        }
        var cause = e.getCause();
        return cause instanceof HttpTimeoutException || cause instanceof ConnectException;
    }
}

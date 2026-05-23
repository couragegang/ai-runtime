package com.couragegang.ai.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public final class AuditClient {

    private static final Logger LOG = LoggerFactory.getLogger(AuditClient.class);

    private final boolean enabled;
    private final String ingestUrl;
    private final String internalKey;
    private final HttpClient http;
    private final ObjectMapper json;

    public AuditClient(
            @Value("${ai.audit-service.enabled:true}") boolean enabled,
            @Value("${ai.audit-service.base-url:http://localhost:8086/v1/audit}") String baseUrl,
            @Value("${ai.audit-service.internal-api-key:dev-internal-key}") String internalKey) {
        this.enabled = enabled;
        var base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.ingestUrl = base + "/internal/tool-events";
        this.internalKey = internalKey;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.json = new ObjectMapper();
    }

    /**
     * @param eventType {@code ai.chat} or {@code ai.tool_call}
     * @param toolName tool id or {@code chat} for message-only
     * @param outcome chat status: stub, completed, denied, awaiting_approval, error
     */
    public void emitChatEvent(
            UUID orgId,
            UUID workspaceId,
            UUID actorUserId,
            String connectorKey,
            String eventType,
            String toolName,
            String outcome,
            Map<String, Object> metadata) {
        if (!enabled || orgId == null) {
            return;
        }
        try {
            ObjectNode body = json.createObjectNode();
            body.put("orgId", orgId.toString());
            if (workspaceId != null) {
                body.put("workspaceId", workspaceId.toString());
            }
            body.put("eventType", eventType);
            body.put("toolName", toolName);
            body.put("outcome", outcome);
            if (actorUserId != null) {
                body.put("actorUserId", actorUserId.toString());
            }
            ObjectNode meta = json.createObjectNode();
            if (connectorKey != null && !connectorKey.isBlank()) {
                meta.put("connectorKey", connectorKey);
            }
            if (metadata != null) {
                metadata.forEach(
                        (k, v) -> {
                            if (v != null) {
                                meta.putPOJO(k, v);
                            }
                        });
            }
            body.set("metadata", meta);
            var request =
                    HttpRequest.newBuilder(URI.create(ingestUrl))
                            .timeout(Duration.ofSeconds(5))
                            .header("Content-Type", "application/json")
                            .header("X-Audit-Internal-Key", internalKey)
                            .POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            json.writeValueAsString(body), StandardCharsets.UTF_8))
                            .build();
            http.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            LOG.debug("audit emit skipped: {}", e.toString());
        }
    }
}

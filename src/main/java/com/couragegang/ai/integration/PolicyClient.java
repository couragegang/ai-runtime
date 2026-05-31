package com.couragegang.ai.integration;

import com.couragegang.ai.metrics.OutboundHttpMetrics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public final class PolicyClient {

    private static final Logger LOG = LoggerFactory.getLogger(PolicyClient.class);

    private final boolean enabled;
    private final String evaluateUrl;
    private final String internalKey;
    private final HttpClient http;
    private final OutboundHttpMetrics metrics;
    private final ObjectMapper json;

    public PolicyClient(
            @Value("${ai.policy-service.enabled:true}") boolean enabled,
            @Value("${ai.policy-service.base-url:http://localhost:8085/v1/policy}") String baseUrl,
            @Value("${ai.policy-service.internal-api-key:dev-internal-key}") String internalKey,
            OutboundHttpMetrics metrics) {
        this.enabled = enabled;
        var base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.evaluateUrl = base + "/internal/evaluate";
        this.internalKey = internalKey;
        this.metrics = metrics;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.json = new ObjectMapper();
    }

    public Optional<EvaluateResult> evaluate(
            UUID orgId,
            UUID workspaceId,
            String connectorKey,
            String toolName,
            UUID userId,
            Map<String, Object> toolArguments) {
        if (!enabled) {
            return Optional.of(new EvaluateResult("allow", null));
        }
        try {
            var bodyNode = json.createObjectNode();
            bodyNode.put("orgId", orgId.toString());
            bodyNode.put("workspaceId", workspaceId.toString());
            bodyNode.put("connectorKey", connectorKey);
            bodyNode.put("toolName", toolName);
            bodyNode.set(
                    "toolArguments",
                    json.valueToTree(toolArguments != null ? toolArguments : Map.of()));
            if (userId != null) {
                bodyNode.put("userId", userId.toString());
            }
            var body = json.writeValueAsString(bodyNode);
            var request =
                    HttpRequest.newBuilder(URI.create(evaluateUrl))
                            .timeout(Duration.ofSeconds(10))
                            .header("Content-Type", "application/json")
                            .header("X-Policy-Internal-Key", internalKey)
                            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                            .build();
            var response = metrics.send(http, request, "policy", "evaluate");
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOG.warn("policy evaluate failed: {} {}", response.statusCode(), response.body());
                return Optional.empty();
            }
            JsonNode node = json.readTree(response.body());
            var pending = node.path("pendingApprovalId");
            return Optional.of(
                    new EvaluateResult(
                            node.path("decision").asText("allow"),
                            pending.isNull() || pending.isMissingNode() ? null : UUID.fromString(pending.asText())));
        } catch (Exception e) {
            LOG.warn("policy evaluate error: {}", e.toString());
            return Optional.empty();
        }
    }

    public Optional<PendingApprovalInfo> getPendingApproval(UUID pendingId) {
        if (!enabled || pendingId == null) {
            return Optional.empty();
        }
        try {
            var base = evaluateUrl.replace("/internal/evaluate", "");
            var request =
                    HttpRequest.newBuilder(URI.create(base + "/pending-approvals/" + pendingId))
                            .timeout(Duration.ofSeconds(10))
                            .GET()
                            .build();
            var response = metrics.send(http, request, "policy", "get_pending");
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOG.warn("policy get pending failed: {} {}", response.statusCode(), response.body());
                return Optional.empty();
            }
            JsonNode node = json.readTree(response.body());
            var toolName = node.path("toolName").asText(null);
            if (toolName == null || toolName.isBlank()) {
                return Optional.empty();
            }
            Map<String, Object> toolArguments = Map.of();
            var argsNode = node.path("toolArguments");
            if (argsNode.isObject()) {
                @SuppressWarnings("unchecked")
                var parsed = (Map<String, Object>) json.convertValue(argsNode, Map.class);
                if (parsed != null) {
                    toolArguments = parsed;
                }
            }
            return Optional.of(
                    new PendingApprovalInfo(
                            pendingId,
                            node.path("status").asText("pending"),
                            toolName,
                            node.path("workspaceId").asText(null),
                            toolArguments));
        } catch (Exception e) {
            LOG.warn("policy get pending error: {}", e.toString());
            return Optional.empty();
        }
    }

    public record EvaluateResult(String decision, UUID pendingApprovalId) {}

    public record PendingApprovalInfo(
            UUID id, String status, String toolName, String workspaceId, Map<String, Object> toolArguments) {}
}

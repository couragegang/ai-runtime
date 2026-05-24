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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public final class McpToolClient {

    private static final Logger LOG = LoggerFactory.getLogger(McpToolClient.class);

    private final boolean enabled;
    private final String invokeUrlTemplate;
    private final String internalKey;
    private final HttpClient http;
    private final OutboundHttpMetrics metrics;
    private final ObjectMapper json;

    public McpToolClient(
            @Value("${ai.mcp-service.enabled:true}") boolean enabled,
            @Value("${ai.mcp-service.base-url:http://localhost:8081/v1/mcp}") String baseUrl,
            @Value("${ai.mcp-service.internal-api-key:dev-internal-key}") String internalKey,
            OutboundHttpMetrics metrics) {
        this.enabled = enabled;
        var base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.invokeUrlTemplate = base + "/internal/workspaces/%s/tools/invoke";
        this.internalKey = internalKey;
        this.metrics = metrics;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.json = new ObjectMapper();
    }

    public Optional<InvokeResult> invoke(
            UUID workspaceId, String connectorKey, String toolName, Map<String, Object> arguments) {
        if (!enabled) {
            return Optional.empty();
        }
        try {
            var bodyNode = json.createObjectNode();
            bodyNode.put("connectorKey", connectorKey);
            bodyNode.put("toolName", toolName);
            bodyNode.set("arguments", json.valueToTree(arguments != null ? arguments : Map.of()));
            var body = json.writeValueAsString(bodyNode);
            var url = String.format(invokeUrlTemplate, workspaceId);
            var request =
                    HttpRequest.newBuilder(URI.create(url))
                            .timeout(Duration.ofSeconds(60))
                            .header("Content-Type", "application/json")
                            .header("X-Mcp-Internal-Key", internalKey)
                            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                            .build();
            var response = metrics.send(http, request, "mcp", "invoke_tool");
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOG.warn("mcp invoke failed: {} {}", response.statusCode(), response.body());
                return Optional.of(InvokeResult.failure("MCP invoke HTTP " + response.statusCode()));
            }
            JsonNode node = json.readTree(response.body());
            if (node.path("ok").asBoolean(false)) {
                return Optional.of(InvokeResult.success(node.path("summary").asText("")));
            }
            return Optional.of(InvokeResult.failure(node.path("error").asText("tool failed")));
        } catch (Exception e) {
            LOG.warn("mcp invoke error: {}", e.toString());
            return Optional.of(InvokeResult.failure("MCP: " + e.getMessage()));
        }
    }

    public Map<String, Object> toolArguments(String message) {
        var args = new LinkedHashMap<String, Object>();
        args.put("content", message);
        args.put("message", message);
        args.put("query", message);
        return args;
    }

    public record InvokeResult(boolean ok, String summary, String error) {
        public static InvokeResult success(String summary) {
            return new InvokeResult(true, summary, null);
        }

        public static InvokeResult failure(String error) {
            return new InvokeResult(false, null, error);
        }
    }
}

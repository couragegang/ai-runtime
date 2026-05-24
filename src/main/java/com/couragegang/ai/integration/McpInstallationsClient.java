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
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public final class McpInstallationsClient {

    private static final Logger LOG = LoggerFactory.getLogger(McpInstallationsClient.class);

    private final boolean enabled;
    private final String listUrlTemplate;
    private final HttpClient http;
    private final OutboundHttpMetrics metrics;
    private final ObjectMapper json;

    public McpInstallationsClient(
            @Value("${ai.mcp-service.enabled:true}") boolean enabled,
            @Value("${ai.mcp-service.base-url:http://localhost:8081/v1/mcp}") String baseUrl,
            OutboundHttpMetrics metrics) {
        this.enabled = enabled;
        var base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.listUrlTemplate = base + "/workspaces/%s/installations";
        this.metrics = metrics;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.json = new ObjectMapper();
    }

    public Set<String> activeConnectorKeys(UUID workspaceId) {
        if (!enabled) {
            return Set.of();
        }
        try {
            var url = String.format(listUrlTemplate, workspaceId);
            var request =
                    HttpRequest.newBuilder(URI.create(url))
                            .timeout(Duration.ofSeconds(10))
                            .GET()
                            .build();
            var response = metrics.send(http, request, "mcp", "list_installations");
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOG.warn("mcp list installations failed: {}", response.statusCode());
                return Set.of();
            }
            var keys = new LinkedHashSet<String>();
            JsonNode items = json.readTree(response.body()).path("items");
            if (items.isArray()) {
                for (var item : items) {
                    var status = item.path("status").asText("");
                    if ("active".equals(status) || "error".equals(status)) {
                        keys.add(item.path("connectorKey").asText());
                    }
                }
            }
            return keys;
        } catch (Exception e) {
            LOG.warn("mcp list installations error: {}", e.toString());
            return Set.of();
        }
    }
}

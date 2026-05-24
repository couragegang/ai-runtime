package com.couragegang.ai.service;

import com.couragegang.ai.api.dto.OrchestratorDtos.ChatTurnDto;
import com.couragegang.ai.api.dto.OrchestratorDtos.InternalRouteRequest;
import com.couragegang.ai.api.dto.OrchestratorDtos.OrchestratorPlan;
import com.couragegang.ai.api.dto.OrchestratorDtos.PlanStep;
import com.couragegang.ai.integration.DeepSeekClient;
import com.couragegang.ai.integration.DeepSeekClient.DeepSeekException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public final class OrchestratorRouterService {

    private static final Logger LOG = LoggerFactory.getLogger(OrchestratorRouterService.class);

    private final DeepSeekClient deepSeek;
    private final OrchestratorToolCatalog catalog;
    private final ObjectMapper json;

    public OrchestratorRouterService(DeepSeekClient deepSeek, OrchestratorToolCatalog catalog) {
        this.deepSeek = deepSeek;
        this.catalog = catalog;
        this.json = new ObjectMapper();
    }

    public OrchestratorPlan route(InternalRouteRequest request) {
        var connectors =
                request.activeConnectorKeys() != null ? request.activeConnectorKeys() : List.<String>of();
        var tools = catalog.toolsForConnectors(Set.copyOf(connectors));
        if (tools.isEmpty()) {
            return new OrchestratorPlan("chat", List.of(), "no active connectors");
        }
        try {
            var system = buildRouterSystemPrompt(tools);
            var user = buildRouterUserPayload(request, tools);
            var raw = deepSeek.completeWithSystem(system, user);
            return parsePlan(raw, connectors);
        } catch (DeepSeekException e) {
            LOG.warn("router deepseek failed: {}", e.getMessage());
            return new OrchestratorPlan("chat", List.of(), "router fallback: " + e.getMessage());
        } catch (Exception e) {
            LOG.warn("router parse failed: {}", e.toString());
            return new OrchestratorPlan("chat", List.of(), "router fallback");
        }
    }

    private String buildRouterSystemPrompt(List<OrchestratorToolCatalog.ToolDefinition> tools) {
        var sb = new StringBuilder();
        sb.append(
                """
                You are a tool router for a B2B chat assistant. Choose zero or more MCP tool steps, or pure chat.
                Reply with ONLY valid JSON (no markdown), schema:
                {
                  "mode": "chat" | "tool_chain",
                  "steps": [
                    {
                      "connectorKey": "string",
                      "toolName": "string",
                      "arguments": { },
                      "label": "short human label in Russian"
                    }
                  ],
                  "reasoning": "brief"
                }
                Rules:
                - Use ONLY tools listed below for active connectors.
                - mode=chat when no external tool is needed.
                - mode=tool_chain when one or more tools must run IN ORDER (e.g. search then write).
                - Never invent connectorKey or toolName.
                - arguments must match the user intent (query for search, title/content for write).
                - Do not claim tools ran; only plan them.

                Available tools:
                """);
        for (var t : tools) {
            sb.append("- connectorKey=")
                    .append(t.connectorKey())
                    .append(" toolName=")
                    .append(t.toolName())
                    .append(" — ")
                    .append(t.displayName())
                    .append(": ")
                    .append(t.description())
                    .append("\n");
        }
        return sb.toString();
    }

    private String buildRouterUserPayload(
            InternalRouteRequest request, List<OrchestratorToolCatalog.ToolDefinition> tools)
            throws Exception {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("message", request.message());
        payload.put("activeConnectorKeys", request.activeConnectorKeys());
        payload.put("history", request.messages() != null ? request.messages() : List.of());
        payload.put("tools", tools);
        return json.writeValueAsString(payload);
    }

    private OrchestratorPlan parsePlan(String raw, List<String> allowedConnectors) throws Exception {
        var text = stripMarkdownFence(raw);
        JsonNode root = json.readTree(text);
        var mode = root.path("mode").asText("chat");
        var steps = new ArrayList<PlanStep>();
        var allowedTools = catalog.toolsForConnectors(Set.copyOf(allowedConnectors));
        for (var node : root.path("steps")) {
            var connectorKey = node.path("connectorKey").asText(null);
            var toolName = node.path("toolName").asText(null);
            if (connectorKey == null || toolName == null) {
                continue;
            }
            if (!isAllowedTool(allowedTools, connectorKey, toolName)) {
                continue;
            }
            Map<String, Object> args = Map.of();
            if (node.has("arguments") && node.get("arguments").isObject()) {
                args = json.convertValue(node.get("arguments"), Map.class);
            }
            var label = node.path("label").asText(null);
            steps.add(new PlanStep(connectorKey, toolName, args, label));
        }
        if (steps.isEmpty()) {
            return new OrchestratorPlan("chat", List.of(), root.path("reasoning").asText(null));
        }
        return new OrchestratorPlan("tool_chain", List.copyOf(steps), root.path("reasoning").asText(null));
    }

    private static boolean isAllowedTool(
            List<OrchestratorToolCatalog.ToolDefinition> allowed, String connectorKey, String toolName) {
        return allowed.stream()
                .anyMatch(
                        t ->
                                t.connectorKey().equals(connectorKey)
                                        && t.toolName().equals(toolName));
    }

    private static String stripMarkdownFence(String raw) {
        if (raw == null) {
            return "{}";
        }
        var t = raw.trim();
        if (t.startsWith("```")) {
            var nl = t.indexOf('\n');
            if (nl > 0) {
                t = t.substring(nl + 1);
            }
            if (t.endsWith("```")) {
                t = t.substring(0, t.length() - 3).trim();
            }
        }
        return t;
    }
}

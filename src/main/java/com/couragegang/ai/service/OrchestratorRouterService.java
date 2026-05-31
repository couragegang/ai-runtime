package com.couragegang.ai.service;

import com.couragegang.ai.api.dto.OrchestratorDtos.ConnectorTask;
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
import java.util.Locale;
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
        var connectorSet = Set.copyOf(connectors);
        var capabilities = catalog.connectorCapabilities(connectorSet);
        if (capabilities.isEmpty()) {
            return new OrchestratorPlan("chat", List.of(), "no active connectors", false);
        }
        try {
            var system = buildRouterSystemPrompt(capabilities);
            var user = buildRouterUserPayload(request, capabilities);
            var raw = deepSeek.completeWithSystem(system, user);
            return parsePlan(raw, connectors);
        } catch (DeepSeekException e) {
            LOG.warn("router deepseek failed: {}", e.getMessage());
            return new OrchestratorPlan("chat", List.of(), "router fallback: " + e.getMessage(), false);
        } catch (Exception e) {
            LOG.warn("router parse failed: {}", e.toString());
            return new OrchestratorPlan("chat", List.of(), "router fallback", false);
        }
    }

    private String buildRouterSystemPrompt(List<OrchestratorToolCatalog.ConnectorCapability> capabilities) {
        var sb = new StringBuilder();
        sb.append(
                """
                You are a connector router for a B2B chat assistant. Plan connector-level steps or pure chat.
                Reply with ONLY valid JSON (no markdown), schema:
                {
                  "mode": "chat" | "connector_chain",
                  "steps": [
                    {
                      "connectorKey": "string",
                      "task": { "message": "user intent for this connector", "constraints": { } },
                      "label": "short human label in Russian"
                    }
                  ],
                  "reasoning": "brief",
                  "requiresPlanApproval": false
                }
                Rules:
                - Use mode=connector_chain when external connectors must act; one step per connector with task.message.
                - Never set toolName on steps — each connector workflow picks internal tools (search/write/edit, etc.).
                - mode=chat when no external connector action is needed.
                - task.message must reflect the user request for that connector.
                - task.constraints may include page_hint, channel, etc.
                - requiresPlanApproval: true when the user must confirm the ORDER of steps before execution
                  (use for 2+ connector steps, 2+ different connectors, or cross-MCP chains).
                - Optional per step: skipIf (priorFailed | priorOk:0 | priorConnector:notion.failed),
                  onFailure (continue | abort | skip_remaining) for conditional chains.
                - Do not claim tools ran; only plan them.

                Active connectors (plan at this level only):
                """);
        for (var c : capabilities) {
            sb.append("- ")
                    .append(c.connectorKey())
                    .append(" — ")
                    .append(c.displayName())
                    .append(": ")
                    .append(c.description())
                    .append("\n");
        }
        return sb.toString();
    }

    private String buildRouterUserPayload(
            InternalRouteRequest request, List<OrchestratorToolCatalog.ConnectorCapability> capabilities)
            throws Exception {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("message", request.message());
        payload.put("activeConnectorKeys", request.activeConnectorKeys());
        payload.put("history", request.messages() != null ? request.messages() : List.of());
        payload.put("connectors", capabilities);
        return json.writeValueAsString(payload);
    }

    private OrchestratorPlan parsePlan(String raw, List<String> allowedConnectors) throws Exception {
        var text = stripMarkdownFence(raw);
        JsonNode root = json.readTree(text);
        var mode = root.path("mode").asText("chat");
        var steps = new ArrayList<PlanStep>();
        var allowedConnectorSet = Set.copyOf(allowedConnectors);
        var allowedRouterTools = catalog.toolsForRouter(allowedConnectorSet);

        for (var node : root.path("steps")) {
            var connectorKey = node.path("connectorKey").asText(null);
            if (connectorKey == null || !allowedConnectorSet.contains(connectorKey)) {
                continue;
            }
            var label = node.path("label").asText(null);
            var toolName = node.path("toolName").asText(null);
            var skipIf = optionalText(node, "skipIf");
            var onFailure = optionalText(node, "onFailure");

            if (node.has("task") && node.get("task").isObject()) {
                var taskNode = node.get("task");
                var message = taskNode.path("message").asText(null);
                if (message == null || message.isBlank()) {
                    continue;
                }
                Map<String, Object> constraints = Map.of();
                if (taskNode.has("constraints") && taskNode.get("constraints").isObject()) {
                    constraints = json.convertValue(taskNode.get("constraints"), Map.class);
                }
                List<String> inputsFromPrior = List.of();
                if (taskNode.has("inputsFromPrior") && taskNode.get("inputsFromPrior").isArray()) {
                    inputsFromPrior =
                            json.convertValue(
                                    taskNode.get("inputsFromPrior"),
                                    json.getTypeFactory().constructCollectionType(List.class, String.class));
                }
                steps.add(
                        new PlanStep(
                                connectorKey,
                                null,
                                null,
                                new ConnectorTask(message, constraints, inputsFromPrior),
                                label,
                                skipIf,
                                onFailure));
                continue;
            }

            if (OrchestratorToolCatalog.hasConnectorWorkflow(connectorKey)) {
                var taskMessage = taskMessageFromLegacyToolStep(node, toolName, label);
                if (taskMessage == null || taskMessage.isBlank()) {
                    continue;
                }
                Map<String, Object> constraints = Map.of();
                if (node.has("arguments") && node.get("arguments").isObject()) {
                    constraints = json.convertValue(node.get("arguments"), Map.class);
                }
                steps.add(
                        new PlanStep(
                                connectorKey,
                                null,
                                null,
                                new ConnectorTask(taskMessage, constraints, List.of()),
                                label,
                                skipIf,
                                onFailure));
                continue;
            }

            if (toolName == null || !isAllowedTool(allowedRouterTools, connectorKey, toolName)) {
                continue;
            }
            Map<String, Object> args = Map.of();
            if (node.has("arguments") && node.get("arguments").isObject()) {
                args = json.convertValue(node.get("arguments"), Map.class);
            }
            steps.add(new PlanStep(connectorKey, toolName, args, null, label, skipIf, onFailure));
        }

        if (steps.isEmpty()) {
            return new OrchestratorPlan("chat", List.of(), root.path("reasoning").asText(null), false);
        }

        var resolvedMode = mode;
        if ("connector_chain".equals(mode) || steps.stream().anyMatch(s -> s.task() != null)) {
            resolvedMode = "connector_chain";
        } else if (!"tool_chain".equals(mode)) {
            resolvedMode = "tool_chain";
        }
        var requiresPlan =
                root.has("requiresPlanApproval") && !root.get("requiresPlanApproval").isNull()
                        ? root.get("requiresPlanApproval").asBoolean(false)
                        : computeRequiresPlanApproval(steps);
        return new OrchestratorPlan(
                resolvedMode, List.copyOf(steps), root.path("reasoning").asText(null), requiresPlan);
    }

    private static String optionalText(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        var text = node.get(field).asText(null);
        return text != null && !text.isBlank() ? text.trim() : null;
    }

    static boolean computeRequiresPlanApproval(List<PlanStep> steps) {
        if (steps == null || steps.size() < 2) {
            return false;
        }
        var keys =
                steps.stream()
                        .map(PlanStep::connectorKey)
                        .filter(k -> k != null && !k.isBlank())
                        .map(k -> k.trim().toLowerCase(Locale.ROOT))
                        .distinct()
                        .count();
        return keys >= 2 || steps.size() >= 2;
    }

    private String taskMessageFromLegacyToolStep(JsonNode node, String toolName, String label) {
        if (label != null && !label.isBlank()) {
            return label.strip();
        }
        if (node.has("arguments") && node.get("arguments").isObject()) {
            var args = node.get("arguments");
            for (var field : List.of("message", "query", "content")) {
                if (args.has(field) && !args.get(field).isNull()) {
                    var text = args.get(field).asText(null);
                    if (text != null && !text.isBlank()) {
                        return text.strip();
                    }
                }
            }
        }
        return toolName != null ? toolName : null;
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

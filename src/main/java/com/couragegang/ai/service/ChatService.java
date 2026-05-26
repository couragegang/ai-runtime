package com.couragegang.ai.service;

import com.couragegang.ai.api.dto.ChatRequest;
import com.couragegang.ai.api.dto.ChatResponse;
import com.couragegang.ai.integration.AuditClient;
import com.couragegang.ai.integration.McpInstallationsClient;
import com.couragegang.ai.integration.McpToolClient;
import com.couragegang.ai.integration.McpToolClient.InvokeResult;
import com.couragegang.ai.integration.PolicyClient;
import com.couragegang.ai.service.ToolIntentResolver.ResolvedTool;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Singleton
public final class ChatService {

    private final PolicyClient policy;
    private final LlmService llm;
    private final AuditClient audit;
    private final ConversationService conversations;
    private final McpToolClient mcpTool;
    private final McpInstallationsClient mcpInstallations;
    private final ToolIntentResolver toolIntent;
    private final OrchestratorService orchestrator;

    public ChatService(
            PolicyClient policy,
            LlmService llm,
            AuditClient audit,
            ConversationService conversations,
            McpToolClient mcpTool,
            McpInstallationsClient mcpInstallations,
            ToolIntentResolver toolIntent,
            OrchestratorService orchestrator) {
        this.policy = policy;
        this.llm = llm;
        this.audit = audit;
        this.conversations = conversations;
        this.mcpTool = mcpTool;
        this.mcpInstallations = mcpInstallations;
        this.toolIntent = toolIntent;
        this.orchestrator = orchestrator;
    }

    public ChatResponse chat(ChatRequest request) {
        if (request.workspaceId() == null) {
            return statelessChat(request);
        }
        var ctx =
                new ConversationService.ChatContext(
                        request.workspaceId(),
                        request.orgId(),
                        request.userId(),
                        request.conversationId(),
                        request.message());
        var conversationId = conversations.ensureConversation(ctx);

        if (orchestrator.useN8n()) {
            return orchestrator.chatViaN8n(request);
        }
        if (request.approvedPendingApprovalId() != null) {
            return executeApprovedTool(request, conversationId);
        }

        var needsTitle = conversations.countMessages(conversationId) == 0;
        conversations.appendUserMessage(conversationId, request.message());
        String conversationTitle = null;
        if (needsTitle) {
            conversationTitle =
                    conversations
                            .generateAndUpdateTitle(request.workspaceId(), conversationId, request.message())
                            .orElse(null);
        }

        var ws = request.workspaceId().toString();
        var activeConnectors = mcpInstallations.activeConnectorKeys(request.workspaceId());
        var history = conversations.historyForLlm(conversationId, 30);
        var toolContextMessage = buildToolContextMessage(history);
        var resolvedTool = resolveTool(request, activeConnectors);
        if (resolvedTool.isPresent() && request.orgId() != null) {
            var tool = resolvedTool.get();
            var policyOutcome = evaluatePolicy(request, tool.connectorKey(), tool.toolName());
            if (policyOutcome.awaitingApproval()) {
                var response =
                        new ChatResponse(
                                conversationId,
                                "Для выполнения действия требуется подтверждение: " + tool.toolName(),
                                "awaiting_approval",
                                policyOutcome.pendingApprovalId(),
                                conversationTitle,
                                tool.toolName(),
                                tool.connectorKey());
                conversations.appendAssistantMessage(
                        conversationId,
                        response.reply(),
                        response.status(),
                        response.pendingApprovalId(),
                        tool.toolName(),
                        tool.connectorKey());
                recordAudit(request, response, tool.connectorKey(), tool.toolName(), true);
                return response;
            }
            if (policyOutcome.denied()) {
                var response =
                        new ChatResponse(
                                conversationId,
                                "Действие запрещено политикой: " + tool.toolName(),
                                "denied",
                                null,
                                conversationTitle,
                                tool.toolName(),
                                tool.connectorKey());
                conversations.appendAssistantMessage(
                        conversationId,
                        response.reply(),
                        response.status(),
                        null,
                        tool.toolName(),
                        tool.connectorKey());
                recordAudit(request, response, tool.connectorKey(), tool.toolName(), true);
                return response;
            }
            if (policyOutcome.allowed()) {
                var invoked = executeTool(request, tool.connectorKey(), tool.toolName(), history, toolContextMessage);
                if (invoked.isPresent()) {
                    var result = invoked.get();
                    var response =
                            new ChatResponse(
                                    conversationId,
                                    result.ok() ? result.summary() : ("Ошибка инструмента: " + result.error()),
                                    result.ok() ? "completed" : "error",
                                    null,
                                    conversationTitle,
                                    tool.toolName(),
                                    tool.connectorKey());
                    conversations.appendAssistantMessage(
                            conversationId,
                            response.reply(),
                            response.status(),
                            null,
                            tool.toolName(),
                            tool.connectorKey());
                    recordAudit(request, response, tool.connectorKey(), tool.toolName(), true);
                    return response;
                }
            }
        }

        var mcpContext = buildMcpContextPrompt(activeConnectors);
        var llmResult = llm.completeWithHistory(history, ws, mcpContext);
        var response =
                new ChatResponse(
                        conversationId,
                        llmResult.reply(),
                        llmResult.status(),
                        null,
                        conversationTitle,
                        null,
                        null);
        var isTool = resolvedTool.isPresent() && request.orgId() != null;
        conversations.appendAssistantMessage(
                conversationId,
                response.reply(),
                response.status(),
                null,
                isTool ? resolvedTool.get().toolName() : null,
                isTool ? resolvedTool.get().connectorKey() : null);
        recordAudit(
                request,
                response,
                isTool ? resolvedTool.get().connectorKey() : null,
                isTool ? resolvedTool.get().toolName() : null,
                isTool);
        return response;
    }

    /** Без workspace — прежнее поведение для unit-тестов и legacy. */
    private ChatResponse statelessChat(ChatRequest request) {
        var ws = "none";
        var toolName = request.toolName();
        if (toolName != null
                && !toolName.isBlank()
                && request.orgId() != null
                && request.workspaceId() != null) {
            var connector = request.connectorKey() != null ? request.connectorKey() : "notion";
            var policyOutcome = evaluatePolicy(request, connector, toolName);
            if (policyOutcome.awaitingApproval()) {
                var response =
                        new ChatResponse(
                                null,
                                "Tool call requires approval before execution: " + toolName,
                                "awaiting_approval",
                                policyOutcome.pendingApprovalId(),
                                null,
                                toolName,
                                connector);
                recordAudit(request, response, connector, toolName, true);
                return response;
            }
            if (policyOutcome.denied()) {
                var response =
                        new ChatResponse(
                                null, "Tool call denied by policy: " + toolName, "denied", null, null, toolName, connector);
                recordAudit(request, response, connector, toolName, true);
                return response;
            }
            if (policyOutcome.allowed()) {
                var invoked =
                        executeTool(
                                request,
                                connector,
                                toolName,
                                List.of(),
                                request.message() != null ? request.message() : "");
                if (invoked.isPresent()) {
                    var result = invoked.get();
                    var response =
                            new ChatResponse(
                                    null,
                                    result.ok() ? result.summary() : result.error(),
                                    result.ok() ? "completed" : "error",
                                    null,
                                    null,
                                    toolName,
                                    connector);
                    recordAudit(request, response, connector, toolName, true);
                    return response;
                }
            }
        }
        var llmResult = llm.complete(request.message(), ws, null);
        var response =
                new ChatResponse(null, llmResult.reply(), llmResult.status(), null, null, null, null);
        var isTool = toolName != null && !toolName.isBlank() && request.orgId() != null && request.workspaceId() != null;
        recordAudit(
                request,
                response,
                isTool ? (request.connectorKey() != null ? request.connectorKey() : "notion") : null,
                isTool ? toolName : null,
                isTool);
        return response;
    }

    private ChatResponse executeApprovedTool(ChatRequest request, UUID conversationId) {
        var pendingId = request.approvedPendingApprovalId();
        var pending =
                policy.getPendingApproval(pendingId)
                        .filter(p -> "approved".equals(p.status()))
                        .orElse(null);
        if (pending == null) {
            var response =
                    new ChatResponse(
                            conversationId,
                            "Не удалось выполнить действие: подтверждение не найдено или уже недействительно.",
                            "error",
                            null,
                            null,
                            null,
                            null);
            conversations.appendAssistantMessage(conversationId, response.reply(), response.status(), null, null, null);
            return response;
        }
        var connector =
                request.connectorKey() != null && !request.connectorKey().isBlank()
                        ? request.connectorKey()
                        : "notion";
        var toolName =
                request.toolName() != null && !request.toolName().isBlank()
                        ? request.toolName().trim()
                        : pending.toolName();
        var history = conversations.historyForLlm(conversationId, 30);
        var toolMessage = buildToolContextMessage(history);
        if (toolMessage.isBlank()) {
            toolMessage = request.message();
        }
        var invoked =
                mcpTool.invoke(
                        request.workspaceId(),
                        connector,
                        toolName,
                        NotionToolArguments.forTool(toolName, history, toolMessage));
        ChatResponse response;
        if (invoked.isEmpty()) {
            response =
                    new ChatResponse(
                            conversationId,
                            "Инструмент недоступен. Проверьте подключение в настройках workspace.",
                            "error",
                            null,
                            null,
                            toolName,
                            connector);
        } else {
            var result = invoked.get();
            response =
                    new ChatResponse(
                            conversationId,
                            result.ok() ? result.summary() : ("Ошибка: " + result.error()),
                            result.ok() ? "completed" : "error",
                            null,
                            null,
                            toolName,
                            connector);
        }
        conversations.appendAssistantMessage(
                conversationId,
                response.reply(),
                response.status(),
                null,
                toolName,
                connector);
        recordAudit(request, response, connector, toolName, true);
        return response;
    }

    private Optional<ResolvedTool> resolveTool(ChatRequest request, Set<String> activeConnectors) {
        var explicitTool = request.toolName();
        if (explicitTool != null && !explicitTool.isBlank()) {
            var connector = request.connectorKey() != null ? request.connectorKey() : "notion";
            return Optional.of(new ResolvedTool(connector, explicitTool.trim()));
        }
        return toolIntent.resolve(request.message(), activeConnectors);
    }

    private static String buildToolContextMessage(List<ChatTurn> history) {
        if (history == null || history.isEmpty()) {
            return "";
        }
        var parts = new ArrayList<String>();
        ChatTurn lastAssistant = null;
        for (var turn : history) {
            if ("assistant".equals(turn.role())) {
                lastAssistant = turn;
            } else if ("user".equals(turn.role()) && turn.content() != null && !turn.content().isBlank()) {
                parts.add(turn.content().strip());
            }
        }
        var sb = new StringBuilder();
        if (lastAssistant != null && lastAssistant.content() != null && !lastAssistant.content().isBlank()) {
            sb.append("Контекст ассистента: ").append(lastAssistant.content().strip());
        }
        if (!parts.isEmpty()) {
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            sb.append("Запросы пользователя: ").append(String.join(" | ", parts));
        }
        return sb.toString();
    }

    private PolicyOutcome evaluatePolicy(ChatRequest request, String connectorKey, String toolName) {
        var eval = policy.evaluate(request.orgId(), request.workspaceId(), connectorKey, toolName, request.userId());
        if (eval.isEmpty()) {
            return PolicyOutcome.allow();
        }
        var result = eval.get();
        if ("require_approval".equals(result.decision())) {
            return PolicyOutcome.awaiting(result.pendingApprovalId());
        }
        if ("deny".equals(result.decision())) {
            return PolicyOutcome.deny();
        }
        return PolicyOutcome.allow();
    }

    private Optional<InvokeResult> executeTool(
            ChatRequest request,
            String connectorKey,
            String toolName,
            List<ChatTurn> history,
            String toolContextMessage) {
        return mcpTool.invoke(
                request.workspaceId(),
                connectorKey,
                toolName,
                NotionToolArguments.forTool(toolName, history, toolContextMessage));
    }

    private static String buildMcpContextPrompt(Set<String> activeConnectors) {
        if (activeConnectors == null || activeConnectors.isEmpty()) {
            return null;
        }
        var sb = new StringBuilder("В workspace подключены интеграции: ");
        sb.append(String.join(", ", activeConnectors));
        sb.append(". ");
        if (activeConnectors.contains("notion")) {
            sb.append(
                    "Для записи в Notion пользователь должен явно попросить сохранить/создать страницу — тогда"
                            + " сработает notion_write_page (может потребоваться подтверждение). "
                            + "Для поиска и списка страниц — notion_search. "
                            + "Никогда не выдумывай результаты поиска в Notion — если инструмент не вызывался, так и скажи. ");
        }
        sb.append("Не обещай выполнить действие во внешней системе, если пользователь не просил об этом явно.");
        return sb.toString();
    }

    private void recordAudit(
            ChatRequest request, ChatResponse response, String connectorKey, String toolName, boolean toolCall) {
        if (request.orgId() == null) {
            return;
        }
        var eventType = toolCall ? "ai.tool_call" : "ai.chat";
        var tool = toolCall && toolName != null && !toolName.isBlank() ? toolName : "chat";
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (response.pendingApprovalId() != null) {
            metadata.put("pendingApprovalId", response.pendingApprovalId().toString());
        }
        if (response.conversationId() != null) {
            metadata.put("conversationId", response.conversationId().toString());
        }
        audit.emitChatEvent(
                request.orgId(),
                request.workspaceId(),
                request.userId(),
                connectorKey,
                eventType,
                tool,
                response.status(),
                metadata);
    }

    private record PolicyOutcome(boolean allowed, boolean denied, boolean awaitingApproval, UUID pendingApprovalId) {
        static PolicyOutcome allow() {
            return new PolicyOutcome(true, false, false, null);
        }

        static PolicyOutcome deny() {
            return new PolicyOutcome(false, true, false, null);
        }

        static PolicyOutcome awaiting(UUID pendingId) {
            return new PolicyOutcome(false, false, true, pendingId);
        }
    }
}

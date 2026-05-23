package com.couragegang.ai.service;

import com.couragegang.ai.api.dto.ChatRequest;
import com.couragegang.ai.api.dto.ChatResponse;
import com.couragegang.ai.integration.AuditClient;
import com.couragegang.ai.integration.PolicyClient;
import jakarta.inject.Singleton;
import java.util.LinkedHashMap;
import java.util.Map;

@Singleton
public final class ChatService {

    private final PolicyClient policy;
    private final LlmService llm;
    private final AuditClient audit;

    public ChatService(PolicyClient policy, LlmService llm, AuditClient audit) {
        this.policy = policy;
        this.llm = llm;
        this.audit = audit;
    }

    public ChatResponse chat(ChatRequest request) {
        var ws = request.workspaceId() != null ? request.workspaceId().toString() : "none";
        var toolName = request.toolName();
        if (toolName != null && !toolName.isBlank()
                && request.orgId() != null
                && request.workspaceId() != null) {
            var connector = request.connectorKey() != null ? request.connectorKey() : "notion";
            var eval = policy.evaluate(request.orgId(), request.workspaceId(), connector, toolName, request.userId());
            if (eval.isPresent()) {
                var result = eval.get();
                if ("require_approval".equals(result.decision())) {
                    var response =
                            new ChatResponse(
                                    "Tool call requires approval before execution: " + toolName,
                                    "awaiting_approval",
                                    result.pendingApprovalId());
                    recordAudit(request, response, connector, true);
                    return response;
                }
                if ("deny".equals(result.decision())) {
                    var response = new ChatResponse("Tool call denied by policy: " + toolName, "denied", null);
                    recordAudit(request, response, connector, true);
                    return response;
                }
            }
        }
        var llmResult = llm.complete(request.message(), ws);
        var response = new ChatResponse(llmResult.reply(), llmResult.status(), null);
        var isTool =
                toolName != null
                        && !toolName.isBlank()
                        && request.orgId() != null
                        && request.workspaceId() != null;
        recordAudit(
                request,
                response,
                isTool ? (request.connectorKey() != null ? request.connectorKey() : "notion") : null,
                isTool);
        return response;
    }

    private void recordAudit(ChatRequest request, ChatResponse response, String connectorKey, boolean toolCall) {
        if (request.orgId() == null) {
            return;
        }
        var eventType = toolCall ? "ai.tool_call" : "ai.chat";
        var tool = toolCall && request.toolName() != null && !request.toolName().isBlank()
                ? request.toolName()
                : "chat";
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (response.pendingApprovalId() != null) {
            metadata.put("pendingApprovalId", response.pendingApprovalId().toString());
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
}

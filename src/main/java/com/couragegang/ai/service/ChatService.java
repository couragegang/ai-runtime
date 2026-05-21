package com.couragegang.ai.service;

import com.couragegang.ai.api.dto.ChatRequest;
import com.couragegang.ai.api.dto.ChatResponse;
import com.couragegang.ai.integration.PolicyClient;
import jakarta.inject.Singleton;

@Singleton
public final class ChatService {

    private final PolicyClient policy;

    public ChatService(PolicyClient policy) {
        this.policy = policy;
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
                    return new ChatResponse(
                            "Tool call requires approval before execution: " + toolName,
                            "awaiting_approval",
                            result.pendingApprovalId());
                }
                if ("deny".equals(result.decision())) {
                    return new ChatResponse("Tool call denied by policy: " + toolName, "denied", null);
                }
            }
        }
        return new ChatResponse(
                "Заглушка ai-runtime (workspace=" + ws + "): " + request.message(),
                "stub",
                null);
    }
}

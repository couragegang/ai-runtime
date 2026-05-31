package com.couragegang.ai.api.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class OrchestratorDtos {

    private OrchestratorDtos() {}

    @Serdeable
    public record OrchestratorStartRequest(
            @NotNull UUID runId,
            @NotNull UUID conversationId,
            @Nullable UUID orgId,
            @NotNull UUID workspaceId,
            @Nullable UUID userId,
            @NotBlank String message,
            @Nullable UUID approvedPendingApprovalId,
            @Nullable Boolean resume) {}

    @Serdeable
    public record RunCompleteRequest(
            @NotBlank String status,
            @NotBlank String reply,
            @Nullable UUID pendingApprovalId,
            @Nullable String approvalKind,
            @Nullable String toolName,
            @Nullable String connectorKey) {}

    @Serdeable
    public record InternalMessageView(
            UUID id,
            String role,
            String content,
            @Nullable String status,
            @Nullable UUID pendingApprovalId,
            @Nullable String toolName,
            @Nullable String connectorKey,
            String createdAt) {}

    @Serdeable
    public record InternalMessageListResponse(List<InternalMessageView> items) {}

    @Serdeable
    public record ChatTurnDto(@NotBlank String role, @NotBlank String content) {}

    @Serdeable
    public record InternalLlmCompleteRequest(
            @NotNull List<ChatTurnDto> messages,
            @Nullable String mcpContext,
            @Nullable String workspaceId) {}

    @Serdeable
    public record InternalLlmCompleteResponse(@NotBlank String reply, @NotBlank String status) {}

    @Serdeable
    public record InternalRouteRequest(
            @NotBlank String message,
            @NotNull List<ChatTurnDto> messages,
            @NotNull List<String> activeConnectorKeys,
            @Nullable String knowledgeContext) {}

    @Serdeable
    public record ConnectorTask(
            @NotBlank String message,
            @Nullable Map<String, Object> constraints,
            @Nullable List<String> inputsFromPrior) {}

    /** L1 connector step (ADR-003): task and/or explicit toolName for legacy tool_chain. */
    @Serdeable
    public record PlanStep(
            @NotBlank String connectorKey,
            @Nullable String toolName,
            @Nullable Map<String, Object> arguments,
            @Nullable ConnectorTask task,
            @Nullable String label,
            /** skipIf: priorFailed | priorOk:0 | priorConnector:notion.failed */
            @Nullable String skipIf,
            /** onFailure: continue | abort | skip_remaining */
            @Nullable String onFailure) {}

    @Serdeable
    public record OrchestratorPlan(
            @NotBlank String mode,
            @NotNull List<PlanStep> steps,
            @Nullable String reasoning,
            @Nullable Boolean requiresPlanApproval) {}

    @Serdeable
    public record InternalHitlFormatPlanRequest(
            @NotNull List<PlanStep> steps,
            @Nullable String reasoning) {}

    @Serdeable
    public record InternalHitlFormatRequest(
            @NotBlank String connectorKey,
            @NotBlank String toolName,
            @Nullable Map<String, Object> arguments,
            int stepIndex,
            int totalSteps,
            @Nullable UUID workspaceId) {}

    @Serdeable
    public record HitlFormatResponse(@NotBlank String message) {}
}

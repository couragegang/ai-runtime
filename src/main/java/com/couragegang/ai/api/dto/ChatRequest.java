package com.couragegang.ai.api.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

@Serdeable
public record ChatRequest(
        @Nullable UUID orgId,
        @Nullable UUID workspaceId,
        @Nullable UUID userId,
        @Nullable UUID conversationId,
        @NotBlank String message,
        @Nullable String connectorKey,
        @Nullable String toolName,
        /** После approve: выполнить инструмент без повторного запроса подтверждения. */
        @Nullable UUID approvedPendingApprovalId) {}

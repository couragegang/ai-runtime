package com.couragegang.ai.api.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.annotation.Nullable;
import java.util.UUID;

@Serdeable
public record ChatResponse(
        @Nullable UUID conversationId,
        String reply,
        String status,
        @Nullable UUID pendingApprovalId,
        @Nullable String conversationTitle,
        @Nullable String toolName,
        @Nullable String connectorKey) {}

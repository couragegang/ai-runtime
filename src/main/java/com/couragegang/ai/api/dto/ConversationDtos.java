package com.couragegang.ai.api.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

public final class ConversationDtos {

    private ConversationDtos() {}

    @Serdeable
    public record ConversationView(
            UUID id,
            UUID workspaceId,
            String title,
            String status,
            @Nullable String createdAt,
            @Nullable String updatedAt) {}

    @Serdeable
    public record ConversationListResponse(List<ConversationView> items) {}

    @Serdeable
    public record ConversationCreateRequest(
            @Nullable UUID workspaceId,
            @Nullable String title) {}

    @Serdeable
    public record ConversationPatchRequest(@NotBlank String status) {}

    @Serdeable
    public record MessageView(
            UUID id,
            String role,
            String content,
            @Nullable String status,
            @Nullable UUID pendingApprovalId,
            @Nullable String toolName,
            @Nullable String connectorKey,
            @Nullable String createdAt) {}

    @Serdeable
    public record MessageListResponse(List<MessageView> items) {}
}

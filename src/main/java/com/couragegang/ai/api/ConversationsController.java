package com.couragegang.ai.api;

import com.couragegang.ai.api.dto.ConversationDtos.ConversationCreateRequest;
import com.couragegang.ai.api.dto.ConversationDtos.ConversationListResponse;
import com.couragegang.ai.api.dto.ConversationDtos.ConversationPatchRequest;
import com.couragegang.ai.api.dto.ConversationDtos.ConversationView;
import com.couragegang.ai.api.dto.ConversationDtos.MessageListResponse;
import com.couragegang.ai.service.ConversationService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Patch;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.HttpStatus;
import jakarta.validation.Valid;
import java.util.UUID;

@Controller("/conversations")
public class ConversationsController {

    private final ConversationService conversations;

    public ConversationsController(ConversationService conversations) {
        this.conversations = conversations;
    }

    @Get
    public ConversationListResponse list(
            @QueryValue("workspace_id") UUID workspaceId,
            @QueryValue(value = "include_archived", defaultValue = "false") boolean includeArchived) {
        requireWorkspace(workspaceId);
        return conversations.list(workspaceId, includeArchived);
    }

    @Post
    public HttpResponse<ConversationView> create(
            @QueryValue("workspace_id") UUID workspaceId,
            @QueryValue(value = "org_id", defaultValue = "") String orgId,
            @QueryValue(value = "user_id", defaultValue = "") String userId,
            @Body @Valid ConversationCreateRequest body) {
        requireWorkspace(workspaceId);
        var view =
                conversations.create(
                        workspaceId,
                        parseUuid(orgId),
                        parseUuid(userId),
                        body != null ? body : new ConversationCreateRequest(null, null));
        return HttpResponse.created(view);
    }

    @Get("/{conversationId}/messages")
    public MessageListResponse messages(
            @PathVariable UUID conversationId, @QueryValue("workspace_id") UUID workspaceId) {
        requireWorkspace(workspaceId);
        return conversations.listMessages(workspaceId, conversationId);
    }

    @Patch("/{conversationId}")
    public ConversationView patch(
            @PathVariable UUID conversationId,
            @QueryValue("workspace_id") UUID workspaceId,
            @Body @Valid ConversationPatchRequest body) {
        requireWorkspace(workspaceId);
        if ("archived".equals(body.status())) {
            return conversations.archive(workspaceId, conversationId);
        }
        throw new HttpStatusException(HttpStatus.BAD_REQUEST, "unsupported status: " + body.status());
    }

    @Delete("/{conversationId}")
    public HttpResponse<Void> delete(
            @PathVariable UUID conversationId, @QueryValue("workspace_id") UUID workspaceId) {
        requireWorkspace(workspaceId);
        conversations.delete(workspaceId, conversationId);
        return HttpResponse.noContent();
    }

    private static void requireWorkspace(UUID workspaceId) {
        if (workspaceId == null) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "workspace_id required");
        }
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return UUID.fromString(value);
    }
}

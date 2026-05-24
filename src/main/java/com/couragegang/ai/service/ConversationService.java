package com.couragegang.ai.service;

import com.couragegang.ai.api.dto.ConversationDtos.ConversationCreateRequest;
import com.couragegang.ai.api.dto.ConversationDtos.ConversationListResponse;
import com.couragegang.ai.api.dto.ConversationDtos.ConversationPatchRequest;
import com.couragegang.ai.api.dto.ConversationDtos.ConversationView;
import com.couragegang.ai.api.dto.ConversationDtos.MessageListResponse;
import com.couragegang.ai.api.dto.ConversationDtos.MessageView;
import com.couragegang.ai.repo.ConversationRepository;
import com.couragegang.ai.repo.MessageRepository;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;
import java.time.Instant;
import java.util.UUID;

@Singleton
public final class ConversationService {

    private static final String DEFAULT_TITLE = "Новый чат";

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final LlmService llm;

    public ConversationService(
            ConversationRepository conversations, MessageRepository messages, LlmService llm) {
        this.conversations = conversations;
        this.messages = messages;
        this.llm = llm;
    }

    public ConversationListResponse list(UUID workspaceId, boolean includeArchived) {
        try {
            var items =
                    conversations.listByWorkspace(workspaceId, includeArchived).stream()
                            .map(this::toView)
                            .toList();
            return new ConversationListResponse(items);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public ConversationView create(UUID workspaceId, UUID orgId, UUID userId, ConversationCreateRequest req) {
        try {
            var title =
                    req.title() != null && !req.title().isBlank()
                            ? req.title().trim()
                            : DEFAULT_TITLE;
            var id = conversations.insert(workspaceId, orgId, userId, title);
            return toView(conversations.findById(workspaceId, id).orElseThrow());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public ConversationView archive(UUID workspaceId, UUID conversationId) {
        return patchStatus(workspaceId, conversationId, "archived");
    }

    public void delete(UUID workspaceId, UUID conversationId) {
        try {
            if (!conversations.delete(workspaceId, conversationId)) {
                throw notFound();
            }
        } catch (HttpStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public MessageListResponse listMessages(UUID workspaceId, UUID conversationId) {
        try {
            if (conversations.findById(workspaceId, conversationId).isEmpty()) {
                throw notFound();
            }
            var items =
                    messages.listByConversation(conversationId).stream().map(this::toMessageView).toList();
            return new MessageListResponse(items);
        } catch (HttpStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public UUID ensureConversation(ChatContext ctx) {
        if (ctx.conversationId() != null) {
            try {
                if (conversations.findById(ctx.workspaceId(), ctx.conversationId()).isPresent()) {
                    return ctx.conversationId();
                }
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        try {
            return conversations.insert(ctx.workspaceId(), ctx.orgId(), ctx.userId(), DEFAULT_TITLE);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public int countMessages(UUID conversationId) {
        try {
            return conversations.countMessages(conversationId);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Генерирует короткое название через LLM и сохраняет в БД. */
    public java.util.Optional<String> generateAndUpdateTitle(
            UUID workspaceId, UUID conversationId, String firstMessage) {
        try {
            var title = llm.generateConversationTitle(firstMessage);
            if (!conversations.updateTitle(workspaceId, conversationId, title)) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(title);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public java.util.List<MessageView> listMessagesInternal(UUID conversationId, int limit) {
        try {
            var rows = messages.listByConversation(conversationId, limit);
            return rows.stream().map(this::toMessageView).toList();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public java.util.List<ChatTurn> historyForLlm(UUID conversationId, int maxMessages) {
        try {
            var rows = messages.listByConversation(conversationId);
            var from = Math.max(0, rows.size() - maxMessages);
            return rows.subList(from, rows.size()).stream()
                    .map(row -> new ChatTurn(row.role(), row.content()))
                    .toList();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public void appendUserMessage(UUID conversationId, String content) {
        try {
            messages.insert(conversationId, "user", content, null, null, null, null);
            conversations.touch(conversationId);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public void appendAssistantMessage(
            UUID conversationId,
            String content,
            String status,
            UUID pendingApprovalId,
            String toolName,
            String connectorKey) {
        try {
            messages.insert(conversationId, "assistant", content, status, pendingApprovalId, toolName, connectorKey);
            conversations.touch(conversationId);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private ConversationView patchStatus(UUID workspaceId, UUID conversationId, String status) {
        try {
            if (!conversations.updateStatus(workspaceId, conversationId, status)) {
                throw notFound();
            }
            return toView(conversations.findById(workspaceId, conversationId).orElseThrow());
        } catch (HttpStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private ConversationView toView(ConversationRepository.ConversationRow row) {
        return new ConversationView(
                row.id(),
                row.workspaceId(),
                row.title(),
                row.status(),
                row.createdAt().toString(),
                row.updatedAt().toString());
    }

    private MessageView toMessageView(MessageRepository.MessageRow row) {
        return new MessageView(
                row.id(),
                row.role(),
                row.content(),
                row.status(),
                row.pendingApprovalId(),
                row.toolName(),
                row.connectorKey(),
                row.createdAt() != null ? row.createdAt().toString() : null);
    }

    private static HttpStatusException notFound() {
        return new HttpStatusException(HttpStatus.NOT_FOUND, "conversation not found");
    }

    public record ChatContext(
            UUID workspaceId, UUID orgId, UUID userId, UUID conversationId, String message) {}
}

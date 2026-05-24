package com.couragegang.ai.service;

import com.couragegang.ai.api.dto.ChatRequest;
import com.couragegang.ai.api.dto.ChatResponse;
import com.couragegang.ai.api.dto.ConversationDtos.MessageView;
import com.couragegang.ai.api.dto.OrchestratorDtos.InternalHitlFormatRequest;
import com.couragegang.ai.api.dto.OrchestratorDtos.InternalLlmCompleteRequest;
import com.couragegang.ai.api.dto.OrchestratorDtos.InternalLlmCompleteResponse;
import com.couragegang.ai.api.dto.OrchestratorDtos.InternalRouteRequest;
import com.couragegang.ai.api.dto.OrchestratorDtos.OrchestratorPlan;
import com.couragegang.ai.api.dto.OrchestratorDtos.InternalMessageListResponse;
import com.couragegang.ai.api.dto.OrchestratorDtos.InternalMessageView;
import com.couragegang.ai.api.dto.OrchestratorDtos.OrchestratorStartRequest;
import com.couragegang.ai.api.dto.OrchestratorDtos.RunCompleteRequest;
import com.couragegang.ai.integration.N8nOrchestratorClient;
import io.micronaut.context.annotation.Value;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public final class OrchestratorService {

    private static final Logger LOG = LoggerFactory.getLogger(OrchestratorService.class);

    private final String orchestratorMode;
    private final int waitTimeoutSeconds;
    private final N8nOrchestratorClient n8n;
    private final ConversationService conversations;
    private final OrchestratorRunRegistry runs;
    private final LlmService llm;
    private final OrchestratorRouterService router;
    private final OrchestratorToolCatalog toolCatalog;

    public OrchestratorService(
            @Value("${ai.orchestrator:legacy}") String orchestratorMode,
            @Value("${ai.n8n.wait-timeout-seconds:90}") int waitTimeoutSeconds,
            N8nOrchestratorClient n8n,
            ConversationService conversations,
            OrchestratorRunRegistry runs,
            LlmService llm,
            OrchestratorRouterService router,
            OrchestratorToolCatalog toolCatalog) {
        this.orchestratorMode = orchestratorMode != null ? orchestratorMode.trim().toLowerCase() : "legacy";
        this.waitTimeoutSeconds = waitTimeoutSeconds;
        this.n8n = n8n;
        this.conversations = conversations;
        this.runs = runs;
        this.llm = llm;
        this.router = router;
        this.toolCatalog = toolCatalog;
    }

    @PostConstruct
    void logOrchestratorConfig() {
        LOG.info(
                "orchestrator mode={} n8nClientEnabled={} useN8n={}",
                orchestratorMode,
                n8n.isEnabled(),
                useN8n());
    }

    public boolean useN8n() {
        return "n8n".equals(orchestratorMode) && n8n.isEnabled();
    }

    public ChatResponse chatViaN8n(ChatRequest request) {
        if (request.workspaceId() == null) {
            throw new IllegalArgumentException("workspaceId required for n8n orchestrator");
        }
        var ctx =
                new ConversationService.ChatContext(
                        request.workspaceId(),
                        request.orgId(),
                        request.userId(),
                        request.conversationId(),
                        request.message());
        var conversationId = conversations.ensureConversation(ctx);

        var isResume = request.approvedPendingApprovalId() != null;
        String conversationTitle = null;
        if (!isResume) {
            var needsTitle = conversations.countMessages(conversationId) == 0;
            conversations.appendUserMessage(conversationId, request.message());
            if (needsTitle) {
                conversationTitle =
                        conversations
                                .generateAndUpdateTitle(request.workspaceId(), conversationId, request.message())
                                .orElse(null);
            }
        }

        var runId = UUID.randomUUID();
        runs.register(runId, conversationId);

        var started =
                n8n.triggerRun(
                        new OrchestratorStartRequest(
                                runId,
                                conversationId,
                                request.orgId(),
                                request.workspaceId(),
                                request.userId(),
                                request.message(),
                                request.approvedPendingApprovalId(),
                                null));

        if (!started) {
            runs.discard(runId);
            return new ChatResponse(
                    conversationId,
                    "Не удалось запустить оркестратор n8n. Проверьте N8N_WEBHOOK_URL и workflow.",
                    "error",
                    null,
                    conversationTitle,
                    null,
                    null);
        }

        try {
            var complete = runs.await(runId, waitTimeoutSeconds);
            return new ChatResponse(
                    conversationId,
                    complete.reply(),
                    complete.status(),
                    complete.pendingApprovalId(),
                    conversationTitle,
                    complete.toolName(),
                    complete.connectorKey());
        } catch (TimeoutException e) {
            runs.discard(runId);
            return new ChatResponse(
                    conversationId,
                    "Оркестрация ещё выполняется. Обновите чат через несколько секунд.",
                    "orchestrating",
                    null,
                    conversationTitle,
                    null,
                    null);
        }
    }

    public InternalMessageListResponse listMessagesInternal(UUID conversationId, int limit) {
        var capped = Math.min(Math.max(limit, 1), 100);
        var items =
                conversations.listMessagesInternal(conversationId, capped).stream()
                        .map(this::toInternal)
                        .toList();
        return new InternalMessageListResponse(items);
    }

    public OrchestratorPlan route(InternalRouteRequest request) {
        return router.route(request);
    }

    public String formatHitlApproval(InternalHitlFormatRequest request) {
        var tool = toolCatalog.find(request.connectorKey(), request.toolName());
        return HitlPromptFormatter.formatApprovalRequired(
                tool,
                request.arguments(),
                Math.max(request.stepIndex(), 1),
                Math.max(request.totalSteps(), 1));
    }

    public String formatHitlDenied(InternalHitlFormatRequest request) {
        var tool = toolCatalog.find(request.connectorKey(), request.toolName());
        return HitlPromptFormatter.formatDenied(tool);
    }

    public InternalLlmCompleteResponse completeLlm(InternalLlmCompleteRequest request) {
        var history =
                request.messages().stream()
                        .map(m -> new ChatTurn(m.role(), m.content()))
                        .collect(Collectors.toList());
        var ws =
                request.workspaceId() != null && !request.workspaceId().isBlank()
                        ? request.workspaceId()
                        : "n8n";
        var result = llm.completeWithHistory(history, ws, request.mcpContext());
        return new InternalLlmCompleteResponse(result.reply(), result.status());
    }

    public void completeRun(UUID runId, RunCompleteRequest body) {
        var conversationId = runs.conversationIdFor(runId);
        conversations.appendAssistantMessage(
                conversationId,
                body.reply(),
                body.status(),
                body.pendingApprovalId(),
                body.toolName(),
                body.connectorKey());
        runs.complete(runId, body);
    }

    private InternalMessageView toInternal(MessageView view) {
        return new InternalMessageView(
                view.id(),
                view.role(),
                view.content(),
                view.status(),
                view.pendingApprovalId(),
                view.toolName(),
                view.connectorKey(),
                view.createdAt());
    }
}

package com.couragegang.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.couragegang.ai.api.dto.ChatRequest;
import com.couragegang.ai.api.dto.OrchestratorDtos.InternalHitlFormatRequest;
import com.couragegang.ai.api.dto.OrchestratorDtos.InternalRouteRequest;
import com.couragegang.ai.api.dto.OrchestratorDtos.OrchestratorPlan;
import com.couragegang.ai.api.dto.OrchestratorDtos.RunCompleteRequest;
import com.couragegang.ai.service.OrchestratorToolCatalog.ToolDefinition;
import com.couragegang.ai.integration.N8nOrchestratorClient;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrchestratorServiceTest {

    @Mock N8nOrchestratorClient n8n;
    @Mock ConversationService conversations;
    @Mock LlmService llm;
    @Mock OrchestratorRouterService router;
    @Mock OrchestratorToolCatalog toolCatalog;

    OrchestratorRunRegistry runs;
    OrchestratorService svc;
    ExecutorService executor;

    UUID wsId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        runs = new OrchestratorRunRegistry();
        svc = new OrchestratorService("n8n", 5, n8n, conversations, runs, llm, router, toolCatalog);
        executor = Executors.newSingleThreadExecutor();
        lenient().when(n8n.isEnabled()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void useN8nWhenConfigured() {
        when(n8n.isEnabled()).thenReturn(true);
        assertThat(svc.useN8n()).isTrue();
    }

    @Test
    void chatViaN8nWaitsForCallback() throws Exception {
        when(conversations.ensureConversation(any())).thenReturn(conversationId);
        when(conversations.countMessages(conversationId)).thenReturn(1);
        when(n8n.triggerRun(any())).thenAnswer(
                inv -> {
                    var payload = inv.getArgument(0, com.couragegang.ai.api.dto.OrchestratorDtos.OrchestratorStartRequest.class);
                    executor.submit(
                            () ->
                                    svc.completeRun(
                                            payload.runId(),
                                            new RunCompleteRequest(
                                                    "completed", "from n8n", null, null, null)));
                    return true;
                });

        var res =
                svc.chatViaN8n(new ChatRequest(null, wsId, null, conversationId, "hi", null, null, null));

        assertThat(res.status()).isEqualTo("completed");
        assertThat(res.reply()).isEqualTo("from n8n");
        verify(conversations).appendAssistantMessage(
                eq(conversationId), eq("from n8n"), eq("completed"), eq(null), eq(null), eq(null));
    }

    @Test
    void routeDelegatesToRouter() {
        var plan = new OrchestratorPlan("chat", java.util.List.of(), null);
        when(router.route(org.mockito.ArgumentMatchers.any())).thenReturn(plan);
        var res = svc.route(new InternalRouteRequest("hi", java.util.List.of(), java.util.List.of("notion"), null));
        assertThat(res.mode()).isEqualTo("chat");
    }

    @Test
    void formatHitlApprovalUsesCatalog() {
        when(toolCatalog.find("notion", "notion_write_page"))
                .thenReturn(
                        new ToolDefinition(
                                "notion", "Notion", "notion_write_page", "Запись", "d", true));
        var msg =
                svc.formatHitlApproval(
                        new InternalHitlFormatRequest("notion", "notion_write_page", java.util.Map.of(), 1, 1));
        assertThat(msg).contains("Подтвердить");
    }

    @Test
    void completeRunAppendsMessage() {
        var runId = UUID.randomUUID();
        runs.register(runId, conversationId);
        svc.completeRun(runId, new RunCompleteRequest("completed", "done", null, null, null));
        verify(conversations)
                .appendAssistantMessage(conversationId, "done", "completed", null, null, null);
    }
}

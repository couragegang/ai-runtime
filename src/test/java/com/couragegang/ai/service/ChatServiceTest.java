package com.couragegang.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.couragegang.ai.api.dto.ChatRequest;
import com.couragegang.ai.integration.AuditClient;
import com.couragegang.ai.integration.McpInstallationsClient;
import com.couragegang.ai.integration.McpToolClient;
import com.couragegang.ai.integration.McpToolClient.InvokeResult;
import com.couragegang.ai.integration.PolicyClient;
import com.couragegang.ai.integration.PolicyClient.EvaluateResult;
import com.couragegang.ai.integration.PolicyClient.PendingApprovalInfo;
import com.couragegang.ai.service.LlmService.LlmReply;
import com.couragegang.ai.service.ToolIntentResolver.ResolvedTool;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock PolicyClient policy;
    @Mock LlmService llm;
    @Mock AuditClient audit;
    @Mock ConversationService conversations;
    @Mock McpToolClient mcpTool;
    @Mock McpInstallationsClient mcpInstallations;
    @Mock ToolIntentResolver toolIntent;
    @Mock OrchestratorService orchestrator;

    ChatService svc;
    UUID orgId = UUID.randomUUID();
    UUID wsId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        svc = new ChatService(policy, llm, audit, conversations, mcpTool, mcpInstallations, toolIntent, orchestrator);
        lenient().when(orchestrator.useN8n()).thenReturn(false);
        lenient().when(conversations.ensureConversation(any())).thenReturn(conversationId);
        lenient().when(mcpInstallations.activeConnectorKeys(any())).thenReturn(Set.of());
        lenient().when(toolIntent.resolve(any(), any())).thenReturn(Optional.empty());
        lenient().when(mcpTool.toolArguments(any())).thenAnswer(inv -> Map.of("content", inv.getArgument(0)));
        lenient().when(mcpTool.invoke(any(), any(), any(), any())).thenReturn(Optional.empty());
        lenient().when(conversations.countMessages(any())).thenReturn(1);
        lenient()
                .when(conversations.historyForLlm(any(), anyInt()))
                .thenReturn(List.of(new ChatTurn("user", "hi")));
    }

    private void stubLlm(String workspaceLabel, String message) {
        when(llm.completeWithHistory(any(), eq(workspaceLabel), any()))
                .thenReturn(new LlmReply("Заглушка ai-runtime (workspace=" + workspaceLabel + "): " + message, "stub"));
    }

    @Test
    void stubWhenNoTool() {
        stubLlm(wsId.toString(), "hi");
        var res = svc.chat(new ChatRequest(null, wsId, null, null, "hi", null, null, null));
        assertThat(res.status()).isEqualTo("stub");
        verify(policy, never()).evaluate(any(), any(), any(), any(), any());
        verify(audit, never()).emitChatEvent(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void stubWhenToolWithoutOrg() {
        stubLlm(wsId.toString(), "hi");
        var res = svc.chat(new ChatRequest(null, wsId, null, null, "hi", "notion", "write_page", null));
        assertThat(res.status()).isEqualTo("stub");
        verify(policy, never()).evaluate(any(), any(), any(), any(), any());
    }

    @Test
    void awaitingApprovalWhenPolicyRequires() {
        var pendingId = UUID.randomUUID();
        when(policy.evaluate(any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(new EvaluateResult("require_approval", pendingId)));
        var res =
                svc.chat(
                        new ChatRequest(
                                orgId,
                                wsId,
                                UUID.randomUUID(),
                                null,
                                "run tool",
                                "notion",
                                "notion_write_page",
                                null));
        assertThat(res.status()).isEqualTo("awaiting_approval");
        assertThat(res.pendingApprovalId()).isEqualTo(pendingId);
        verify(llm, never()).completeWithHistory(any(), any(), any());
        verify(mcpTool, never()).invoke(any(), any(), any(), any());
    }

    @Test
    void invokesMcpWhenPolicyAllows() {
        when(policy.evaluate(eq(orgId), eq(wsId), eq("notion"), eq("notion_write_page"), any()))
                .thenReturn(Optional.of(new EvaluateResult("allow", null)));
        when(mcpTool.invoke(eq(wsId), eq("notion"), eq("notion_write_page"), any()))
                .thenReturn(Optional.of(InvokeResult.success("Страница создана")));
        var res =
                svc.chat(
                        new ChatRequest(
                                orgId, wsId, null, null, "save to notion", "notion", "notion_write_page", null));
        assertThat(res.status()).isEqualTo("completed");
        assertThat(res.reply()).contains("Страница создана");
        verify(llm, never()).completeWithHistory(any(), any(), any());
    }

    @Test
    void executeAfterApprovalRunsTool() {
        var pendingId = UUID.randomUUID();
        when(policy.getPendingApproval(pendingId))
                .thenReturn(
                        Optional.of(
                                new PendingApprovalInfo(
                                        pendingId, "approved", "notion_write_page", wsId.toString())));
        when(conversations.historyForLlm(conversationId, 30))
                .thenReturn(
                        List.of(
                                new ChatTurn("user", "создай страницу"),
                                new ChatTurn("assistant", "нужно подтверждение")));
        when(mcpTool.invoke(eq(wsId), eq("notion"), eq("notion_write_page"), any()))
                .thenReturn(Optional.of(InvokeResult.success("Готово: страница создана")));
        var res =
                svc.chat(
                        new ChatRequest(
                                orgId,
                                wsId,
                                UUID.randomUUID(),
                                conversationId,
                                "создай страницу",
                                "notion",
                                "notion_write_page",
                                pendingId));
        assertThat(res.status()).isEqualTo("completed");
        assertThat(res.reply()).contains("Готово");
        verify(mcpTool).invoke(eq(wsId), eq("notion"), eq("notion_write_page"), any());
        verify(conversations, never()).appendUserMessage(any(), any());
    }

    @Test
    void llmReceivesMcpContextWhenConnectorsInstalled() {
        when(mcpInstallations.activeConnectorKeys(wsId)).thenReturn(Set.of("notion"));
        when(llm.completeWithHistory(any(), eq(wsId.toString()), contains("notion")))
                .thenReturn(new LlmReply("ok", "stub"));
        var res = svc.chat(new ChatRequest(orgId, wsId, null, null, "hi", null, null, null));
        assertThat(res.reply()).isEqualTo("ok");
    }

    @Test
    void generatesConversationTitleOnFirstMessage() {
        when(conversations.countMessages(conversationId)).thenReturn(0);
        when(conversations.generateAndUpdateTitle(wsId, conversationId, "first"))
                .thenReturn(Optional.of("My title"));
        stubLlm(wsId.toString(), "first");
        var res = svc.chat(new ChatRequest(orgId, wsId, null, null, "first", null, null, null));
        assertThat(res.conversationTitle()).isEqualTo("My title");
    }

    @Test
    void stubUsesNoneWorkspaceWhenMissing() {
        when(llm.complete("only message", "none", null))
                .thenReturn(new LlmReply("Заглушка ai-runtime (workspace=none): only message", "stub"));
        var res = svc.chat(new ChatRequest(null, null, null, null, "only message", null, null, null));
        assertThat(res.reply()).contains("workspace=none");
    }
}

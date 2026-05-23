package com.couragegang.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.couragegang.ai.api.dto.ChatRequest;
import com.couragegang.ai.integration.AuditClient;
import com.couragegang.ai.integration.PolicyClient;
import com.couragegang.ai.integration.PolicyClient.EvaluateResult;
import com.couragegang.ai.service.LlmService.LlmReply;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    PolicyClient policy;

    @Mock
    LlmService llm;

    @Mock
    AuditClient audit;

    ChatService svc;
    UUID orgId = UUID.randomUUID();
    UUID wsId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        svc = new ChatService(policy, llm, audit);
    }

    private void stubLlm(String workspaceLabel, String message) {
        when(llm.complete(eq(message), eq(workspaceLabel)))
                .thenReturn(new LlmReply("Заглушка ai-runtime (workspace=" + workspaceLabel + "): " + message, "stub"));
    }

    @Test
    void stubWhenNoTool() {
        stubLlm(wsId.toString(), "hi");
        var res = svc.chat(new ChatRequest(null, wsId, null, "hi", null, null));

        assertThat(res.status()).isEqualTo("stub");
        verify(policy, never()).evaluate(any(), any(), any(), any(), any());
        verify(audit, never()).emitChatEvent(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void stubWhenToolWithoutOrg() {
        stubLlm(wsId.toString(), "hi");
        var res = svc.chat(new ChatRequest(null, wsId, null, "hi", "notion", "write_page"));

        assertThat(res.status()).isEqualTo("stub");
        verify(policy, never()).evaluate(any(), any(), any(), any(), any());
    }

    @Test
    void stubWhenBlankToolName() {
        stubLlm(wsId.toString(), "hi");
        var res = svc.chat(new ChatRequest(orgId, wsId, null, "hi", "notion", "  "));

        assertThat(res.status()).isEqualTo("stub");
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
                                "run tool",
                                "notion",
                                "notion_write_page"));

        assertThat(res.status()).isEqualTo("awaiting_approval");
        assertThat(res.pendingApprovalId()).isEqualTo(pendingId);
        verify(llm, never()).complete(any(), any());
        verify(audit)
                .emitChatEvent(
                        eq(orgId),
                        eq(wsId),
                        any(),
                        eq("notion"),
                        eq("ai.tool_call"),
                        eq("notion_write_page"),
                        eq("awaiting_approval"),
                        any());
    }

    @Test
    void deniedByPolicy() {
        when(policy.evaluate(any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(new EvaluateResult("deny", null)));

        var res =
                svc.chat(
                        new ChatRequest(orgId, wsId, null, "x", "notion", "notion_write_page"));

        assertThat(res.status()).isEqualTo("denied");
        verify(llm, never()).complete(any(), any());
        verify(audit)
                .emitChatEvent(
                        eq(orgId),
                        eq(wsId),
                        any(),
                        eq("notion"),
                        eq("ai.tool_call"),
                        eq("notion_write_page"),
                        eq("denied"),
                        any());
    }

    @Test
    void defaultConnectorWhenMissing() {
        when(policy.evaluate(eq(orgId), eq(wsId), eq("notion"), eq("fetch_page"), any()))
                .thenReturn(Optional.of(new EvaluateResult("allow", null)));
        stubLlm(wsId.toString(), "x");

        var res = svc.chat(new ChatRequest(orgId, wsId, null, "x", null, "fetch_page"));

        assertThat(res.status()).isEqualTo("stub");
        verify(policy).evaluate(orgId, wsId, "notion", "fetch_page", null);
    }

    @Test
    void llmWhenPolicyReturnsEmpty() {
        when(policy.evaluate(any(), any(), any(), any(), any())).thenReturn(Optional.empty());
        when(llm.complete("msg", wsId.toString())).thenReturn(new LlmReply("LLM says msg", "completed"));

        var res = svc.chat(new ChatRequest(orgId, wsId, null, "msg", "slack", "write_x"));

        assertThat(res.status()).isEqualTo("completed");
        assertThat(res.reply()).isEqualTo("LLM says msg");
        verify(audit)
                .emitChatEvent(
                        eq(orgId),
                        eq(wsId),
                        any(),
                        eq("slack"),
                        eq("ai.tool_call"),
                        eq("write_x"),
                        eq("completed"),
                        any());
    }

    @Test
    void emitsAiChatWhenOrgPresentWithoutTool() {
        stubLlm(wsId.toString(), "hello");
        svc.chat(new ChatRequest(orgId, wsId, UUID.randomUUID(), "hello", null, null));
        verify(audit)
                .emitChatEvent(
                        eq(orgId),
                        eq(wsId),
                        any(),
                        isNull(),
                        eq("ai.chat"),
                        eq("chat"),
                        eq("stub"),
                        any());
    }

    @Test
    void stubUsesNoneWorkspaceWhenMissing() {
        stubLlm("none", "only message");
        var res = svc.chat(new ChatRequest(null, null, null, "only message", null, null));

        assertThat(res.reply()).contains("workspace=none");
    }
}

package com.couragegang.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.couragegang.ai.api.dto.ChatRequest;
import com.couragegang.ai.integration.PolicyClient;
import com.couragegang.ai.integration.PolicyClient.EvaluateResult;
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

    ChatService svc;
    UUID orgId = UUID.randomUUID();
    UUID wsId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        svc = new ChatService(policy);
    }

    @Test
    void stubWhenNoTool() {
        var res = svc.chat(new ChatRequest(null, wsId, null, "hi", null, null));

        assertThat(res.status()).isEqualTo("stub");
        verify(policy, never()).evaluate(any(), any(), any(), any(), any());
    }

    @Test
    void stubWhenToolWithoutOrg() {
        var res = svc.chat(new ChatRequest(null, wsId, null, "hi", "notion", "write_page"));

        assertThat(res.status()).isEqualTo("stub");
        verify(policy, never()).evaluate(any(), any(), any(), any(), any());
    }

    @Test
    void stubWhenBlankToolName() {
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
    }

    @Test
    void deniedByPolicy() {
        when(policy.evaluate(any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(new EvaluateResult("deny", null)));

        var res =
                svc.chat(
                        new ChatRequest(orgId, wsId, null, "x", "notion", "notion_write_page"));

        assertThat(res.status()).isEqualTo("denied");
    }

    @Test
    void defaultConnectorWhenMissing() {
        when(policy.evaluate(eq(orgId), eq(wsId), eq("notion"), eq("fetch_page"), any()))
                .thenReturn(Optional.of(new EvaluateResult("allow", null)));

        var res = svc.chat(new ChatRequest(orgId, wsId, null, "x", null, "fetch_page"));

        assertThat(res.status()).isEqualTo("stub");
        verify(policy).evaluate(orgId, wsId, "notion", "fetch_page", null);
    }

    @Test
    void stubWhenPolicyReturnsEmpty() {
        when(policy.evaluate(any(), any(), any(), any(), any())).thenReturn(Optional.empty());

        var res = svc.chat(new ChatRequest(orgId, wsId, null, "msg", "slack", "write_x"));

        assertThat(res.status()).isEqualTo("stub");
        assertThat(res.reply()).contains("msg");
    }

    @Test
    void stubUsesNoneWorkspaceWhenMissing() {
        var res = svc.chat(new ChatRequest(null, null, null, "only message", null, null));

        assertThat(res.reply()).contains("workspace=none");
    }
}

package com.couragegang.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        when(policy.evaluate(any(), any(), any(), eq("notion"), any()))
                .thenReturn(Optional.of(new EvaluateResult("allow", null)));

        var res = svc.chat(new ChatRequest(orgId, wsId, null, "x", null, "read_tool"));

        assertThat(res.status()).isEqualTo("stub");
    }
}

package com.couragegang.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import com.couragegang.ai.config.AiProperties;
import com.couragegang.ai.integration.DeepSeekClient;
import com.couragegang.ai.integration.DeepSeekClient.DeepSeekException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LlmServiceTest {

    @Mock
    DeepSeekClient deepSeek;

    AiProperties props;
    LlmService svc;

    @BeforeEach
    void setUp() {
        props = new AiProperties();
        svc = new LlmService(props, deepSeek);
    }

    @Test
    void generateTitleUsesFallbackWhenStub() {
        props.setLlmProvider("stub");
        var title = svc.generateConversationTitle("Как настроить Notion для команды?");
        assertThat(title).contains("Notion");
    }

    @Test
    void generateTitleDefaultForBlank() {
        props.setLlmProvider("stub");
        assertThat(svc.generateConversationTitle("  ")).isEqualTo("Новый чат");
    }

    @Test
    void stubProviderReturnsStubStatus() {
        props.setLlmProvider("stub");
        var r = svc.complete("hello", "ws-1");
        assertThat(r.status()).isEqualTo("stub");
        assertThat(r.reply()).contains("hello");
    }

    @Test
    void deepSeekReturnsCompleted() {
        props.setLlmProvider("deepseek");
        when(deepSeek.complete(eq("hi"), isNull())).thenReturn("Привет!");
        var r = svc.complete("hi", "ws");
        assertThat(r.status()).isEqualTo("completed");
        assertThat(r.reply()).isEqualTo("Привет!");
    }

    @Test
    void generateTitleViaDeepSeek() {
        props.setLlmProvider("deepseek");
        when(deepSeek.completeWithSystem(any(), eq("msg"))).thenReturn("  My Title  ");
        assertThat(svc.generateConversationTitle("msg")).isEqualTo("My Title");
    }

    @Test
    void generateTitleDeepSeekFallbackOnError() {
        props.setLlmProvider("deepseek");
        when(deepSeek.completeWithSystem(any(), any()))
                .thenThrow(new DeepSeekException("down"));
        var longMsg = "x".repeat(100);
        assertThat(svc.generateConversationTitle(longMsg)).endsWith("...");
    }

    @Test
    void stubWithExtraSystemContext() {
        props.setLlmProvider("stub");
        var r = svc.complete("hi", "ws", "notion installed");
        assertThat(r.reply()).contains("[mcp:");
    }

    @Test
    void generateTitleViaDeepSeekSanitizesQuotes() {
        props.setLlmProvider("deepseek");
        when(deepSeek.completeWithSystem(any(), eq("msg")))
                .thenReturn("  \"Long title with quotes\"  \nsecond line");
        assertThat(svc.generateConversationTitle("msg")).isEqualTo("Long title with quotes");
    }

    @Test
    void generateTitleStubTruncatesLongMessage() {
        props.setLlmProvider("stub");
        var longMsg = "а".repeat(100);
        assertThat(svc.generateConversationTitle(longMsg)).endsWith("...");
    }

    @Test
    void generateTitleDeepSeekFallbackTruncates() {
        props.setLlmProvider("deepseek");
        when(deepSeek.completeWithSystem(any(), any())).thenThrow(new DeepSeekException("down"));
        var longMsg = "z".repeat(100);
        assertThat(svc.generateConversationTitle(longMsg)).hasSize(80).endsWith("...");
    }

    @Test
    void deepSeekErrorMapsToErrorStatus() {
        props.setLlmProvider("deepseek");
        when(deepSeek.complete(eq("x"), any())).thenThrow(new DeepSeekException("rate limit"));
        var r = svc.complete("x", "ws");
        assertThat(r.status()).isEqualTo("error");
        assertThat(r.reply()).contains("rate limit");
    }

    @Test
    void completeWithHistoryEmptyDelegatesToComplete() {
        props.setLlmProvider("stub");
        var r = svc.completeWithHistory(List.of(), "ws-1", null);
        assertThat(r.status()).isEqualTo("stub");
        assertThat(r.reply()).contains("workspace=ws-1");
    }

    @Test
    void completeWithHistoryNullDelegatesToComplete() {
        props.setLlmProvider("stub");
        var r = svc.completeWithHistory(null, "ws-1", "ctx");
        assertThat(r.status()).isEqualTo("stub");
        assertThat(r.reply()).contains("[mcp:");
    }

    @Test
    void completeWithHistoryStubUsesLastUserMessage() {
        props.setLlmProvider("stub");
        var r =
                svc.completeWithHistory(
                        List.of(
                                new ChatTurn("user", "first"),
                                new ChatTurn("assistant", "ok"),
                                new ChatTurn("user", "last question")),
                        "ws",
                        null);
        assertThat(r.reply()).contains("last question");
    }

    @Test
    void completeWithHistoryStubWithoutUserUsesEmpty() {
        props.setLlmProvider("stub");
        var r = svc.completeWithHistory(List.of(new ChatTurn("assistant", "only")), "ws", null);
        assertThat(r.reply()).endsWith(": ");
    }

    @Test
    void completeWithHistoryDeepSeekSuccess() {
        props.setLlmProvider("deepseek");
        when(deepSeek.completeWithHistory(any(), eq("ctx"))).thenReturn("answer");
        var r =
                svc.completeWithHistory(
                        List.of(new ChatTurn("user", "q")), "ws", "ctx");
        assertThat(r.status()).isEqualTo("completed");
        assertThat(r.reply()).isEqualTo("answer");
    }

    @Test
    void completeWithHistoryDeepSeekError() {
        props.setLlmProvider("deepseek");
        when(deepSeek.completeWithHistory(any(), any()))
                .thenThrow(new DeepSeekException("timeout"));
        var r =
                svc.completeWithHistory(
                        List.of(new ChatTurn("user", "q")), "ws", null);
        assertThat(r.status()).isEqualTo("error");
        assertThat(r.reply()).contains("timeout");
    }

    @Test
    void sanitizeTitleBlankAfterStripReturnsDefault() {
        props.setLlmProvider("deepseek");
        when(deepSeek.completeWithSystem(any(), any())).thenReturn("   \n  ");
        assertThat(svc.generateConversationTitle("x")).isEqualTo("Новый чат");
    }

    @Test
    void sanitizeTitleTruncatesVeryLongLine() {
        props.setLlmProvider("deepseek");
        when(deepSeek.completeWithSystem(any(), any())).thenReturn("x".repeat(120));
        assertThat(svc.generateConversationTitle("x")).hasSize(80).endsWith("...");
    }

    @Test
    void stubReplyWithoutExtraContext() {
        props.setLlmProvider("stub");
        var r = svc.complete("hi", "ws", "  ");
        assertThat(r.reply()).doesNotContain("[mcp:");
    }
}

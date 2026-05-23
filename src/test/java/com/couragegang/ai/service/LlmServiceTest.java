package com.couragegang.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.couragegang.ai.config.AiProperties;
import com.couragegang.ai.integration.DeepSeekClient;
import com.couragegang.ai.integration.DeepSeekClient.DeepSeekException;
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
    void stubProviderReturnsStubStatus() {
        props.setLlmProvider("stub");
        var r = svc.complete("hello", "ws-1");
        assertThat(r.status()).isEqualTo("stub");
        assertThat(r.reply()).contains("hello");
    }

    @Test
    void deepSeekReturnsCompleted() {
        props.setLlmProvider("deepseek");
        when(deepSeek.complete("hi")).thenReturn("Привет!");
        var r = svc.complete("hi", "ws");
        assertThat(r.status()).isEqualTo("completed");
        assertThat(r.reply()).isEqualTo("Привет!");
    }

    @Test
    void deepSeekErrorMapsToErrorStatus() {
        props.setLlmProvider("deepseek");
        when(deepSeek.complete("x")).thenThrow(new DeepSeekException("rate limit"));
        var r = svc.complete("x", "ws");
        assertThat(r.status()).isEqualTo("error");
        assertThat(r.reply()).contains("rate limit");
    }
}

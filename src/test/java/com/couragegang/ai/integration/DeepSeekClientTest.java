package com.couragegang.ai.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.couragegang.ai.config.AiProperties;
import com.couragegang.ai.integration.DeepSeekClient.DeepSeekException;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeepSeekClientTest {

    HttpServer server;
    String baseUrl;
    AiProperties props;

    @BeforeEach
    void startServer() throws Exception {
        props = new AiProperties();
        props.setLlmProvider("deepseek");
        props.getDeepseek().setApiKey("test-key");
        props.getDeepseek().setModel("deepseek-v4-flash");
        props.getDeepseek().setThinkingType("disabled");
        server = HttpServer.create(new InetSocketAddress(0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        props.getDeepseek().setBaseUrl(baseUrl);
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void completesWithAssistantContent() {
        server.createContext(
                "/chat/completions",
                exchange -> {
                    var body =
                            """
                            {"choices":[{"message":{"role":"assistant","content":"OK from model"}}]}
                            """
                                    .trim();
                    exchange.sendResponseHeaders(200, body.length());
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(body.getBytes(StandardCharsets.UTF_8));
                    }
                });

        var reply = new DeepSeekClient(props).complete("user msg");
        assertThat(reply).isEqualTo("OK from model");
    }

    @Test
    void fallsBackToReasoningContent() {
        server.createContext(
                "/chat/completions",
                exchange -> {
                    var body =
                            """
                            {"choices":[{"message":{"role":"assistant","content":"","reasoning_content":"thought answer"}}]}
                            """
                                    .trim();
                    exchange.sendResponseHeaders(200, body.length());
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(body.getBytes(StandardCharsets.UTF_8));
                    }
                });

        assertThat(new DeepSeekClient(props).complete("q")).isEqualTo("thought answer");
    }

    @Test
    void rejectsMissingApiKey() {
        props.getDeepseek().setApiKey("  ");
        assertThatThrownBy(() -> new DeepSeekClient(props).complete("x"))
                .isInstanceOf(DeepSeekException.class)
                .hasMessageContaining("DEEPSEEK_API_KEY");
    }

    @Test
    void httpErrorThrows() {
        server.createContext("/chat/completions", exchange -> exchange.sendResponseHeaders(429, -1));
        assertThatThrownBy(() -> new DeepSeekClient(props).complete("x"))
                .isInstanceOf(DeepSeekException.class)
                .hasMessageContaining("429");
    }
}

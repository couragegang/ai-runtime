package com.couragegang.ai.api;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import java.util.Map;

@Controller
public final class HealthInfoController {

    @Get("/")
    public Map<String, String> root() {
        return Map.of(
                "service", "ai-runtime",
                "health", "/v1/ai/health",
                "chat", "/v1/ai/chat",
                "conversations", "/v1/ai/conversations");
    }
}

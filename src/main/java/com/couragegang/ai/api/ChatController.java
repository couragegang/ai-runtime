package com.couragegang.ai.api;

import com.couragegang.ai.api.dto.ChatRequest;
import com.couragegang.ai.api.dto.ChatResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.validation.Validated;
import jakarta.validation.Valid;

@Controller("/chat")
@Validated
public class ChatController {

    @Post
    public ChatResponse chat(@Body @Valid ChatRequest request) {
        return new ChatResponse(
                "Заглушка ai-runtime: LLM и вызовы MCP будут подключены позже. Сообщение: " + request.message(),
                "stub"
        );
    }
}

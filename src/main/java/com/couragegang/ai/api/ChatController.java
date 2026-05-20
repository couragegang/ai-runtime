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
        var ws = request.workspaceId() != null ? request.workspaceId().toString() : "none";
        return new ChatResponse(
                "Заглушка ai-runtime (workspace=" + ws + "): " + request.message(),
                "stub"
        );
    }
}

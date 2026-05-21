package com.couragegang.ai.api;

import com.couragegang.ai.api.dto.ChatRequest;
import com.couragegang.ai.api.dto.ChatResponse;
import com.couragegang.ai.service.ChatService;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.validation.Validated;
import jakarta.validation.Valid;

@Controller("/chat")
@Validated
public class ChatController {

    private final ChatService chat;

    public ChatController(ChatService chat) {
        this.chat = chat;
    }

    @Post
    public ChatResponse chat(@Body @Valid ChatRequest request) {
        return chat.chat(request);
    }
}

package com.couragegang.ai.service;

/** Одно сообщение в истории диалога для LLM. */
public record ChatTurn(String role, String content) {}

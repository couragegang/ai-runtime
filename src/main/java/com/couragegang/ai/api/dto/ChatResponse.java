package com.couragegang.ai.api.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ChatResponse(String reply, String status) {}

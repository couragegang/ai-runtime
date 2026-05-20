package com.couragegang.ai.api.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

@Serdeable
public record ChatRequest(@Nullable UUID workspaceId, @NotBlank String message) {}

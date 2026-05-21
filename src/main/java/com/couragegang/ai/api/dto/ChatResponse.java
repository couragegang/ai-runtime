package com.couragegang.ai.api.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.annotation.Nullable;
import java.util.UUID;

@Serdeable
public record ChatResponse(String reply, String status, @Nullable UUID pendingApprovalId) {}

package com.couragegang.ai.api;

import com.couragegang.ai.api.dto.OrchestratorDtos.HitlFormatResponse;
import com.couragegang.ai.api.dto.OrchestratorDtos.InternalHitlFormatRequest;
import com.couragegang.ai.api.dto.OrchestratorDtos.InternalLlmCompleteRequest;
import com.couragegang.ai.api.dto.OrchestratorDtos.InternalLlmCompleteResponse;
import com.couragegang.ai.api.dto.OrchestratorDtos.InternalRouteRequest;
import com.couragegang.ai.api.dto.OrchestratorDtos.OrchestratorPlan;
import com.couragegang.ai.api.dto.OrchestratorDtos.InternalMessageListResponse;
import com.couragegang.ai.api.dto.OrchestratorDtos.RunCompleteRequest;
import com.couragegang.ai.service.OrchestratorService;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.annotation.Status;
import io.micronaut.http.HttpStatus;
import io.micronaut.validation.Validated;
import jakarta.validation.Valid;
import java.util.UUID;

@Controller("/internal")
@Validated
public class InternalOrchestratorController {

    private final OrchestratorService orchestrator;

    public InternalOrchestratorController(OrchestratorService orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Get("/conversations/{conversationId}/messages")
    public InternalMessageListResponse messages(
            @PathVariable UUID conversationId,
            @QueryValue(value = "limit", defaultValue = "30") int limit) {
        return orchestrator.listMessagesInternal(conversationId, limit);
    }

    @Post("/runs/{runId}/complete")
    @Status(HttpStatus.NO_CONTENT)
    public void complete(@PathVariable UUID runId, @Body @Valid RunCompleteRequest body) {
        orchestrator.completeRun(runId, body);
    }

    @Post("/llm/complete")
    public InternalLlmCompleteResponse completeLlm(@Body @Valid InternalLlmCompleteRequest body) {
        return orchestrator.completeLlm(body);
    }

    @Post("/llm/route")
    public OrchestratorPlan route(@Body @Valid InternalRouteRequest body) {
        return orchestrator.route(body);
    }

    @Post("/hitl/format-approval")
    public HitlFormatResponse formatApproval(@Body @Valid InternalHitlFormatRequest body) {
        return new HitlFormatResponse(orchestrator.formatHitlApproval(body));
    }

    @Post("/hitl/format-denied")
    public HitlFormatResponse formatDenied(@Body @Valid InternalHitlFormatRequest body) {
        return new HitlFormatResponse(orchestrator.formatHitlDenied(body));
    }
}

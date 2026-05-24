package com.couragegang.ai.service;

import com.couragegang.ai.api.dto.OrchestratorDtos.RunCompleteRequest;
import jakarta.inject.Singleton;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Singleton
public final class OrchestratorRunRegistry {

    private final ConcurrentHashMap<UUID, PendingRun> pending = new ConcurrentHashMap<>();

    public void register(UUID runId, UUID conversationId) {
        pending.put(runId, new PendingRun(conversationId, new CompletableFuture<>()));
    }

    public UUID conversationIdFor(UUID runId) {
        var run = pending.get(runId);
        if (run == null) {
            throw new IllegalArgumentException("unknown runId: " + runId);
        }
        return run.conversationId();
    }

    public void complete(UUID runId, RunCompleteRequest payload) {
        var run = pending.get(runId);
        if (run == null) {
            throw new IllegalArgumentException("unknown runId: " + runId);
        }
        run.future().complete(payload);
    }

    public RunCompleteRequest await(UUID runId, long timeoutSeconds) throws TimeoutException {
        var run = pending.get(runId);
        if (run == null) {
            throw new IllegalArgumentException("unknown runId: " + runId);
        }
        try {
            var result = run.future().get(timeoutSeconds, TimeUnit.SECONDS);
            pending.remove(runId);
            return result;
        } catch (java.util.concurrent.ExecutionException e) {
            pending.remove(runId);
            throw new IllegalStateException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pending.remove(runId);
            throw new IllegalStateException("interrupted waiting for run", e);
        } catch (TimeoutException e) {
            throw e;
        }
    }

    public void discard(UUID runId) {
        var run = pending.remove(runId);
        if (run != null) {
            run.future().cancel(true);
        }
    }

    private record PendingRun(UUID conversationId, CompletableFuture<RunCompleteRequest> future) {}
}

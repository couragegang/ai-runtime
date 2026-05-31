package com.couragegang.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.couragegang.ai.api.dto.OrchestratorDtos.RunCompleteRequest;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class OrchestratorRunRegistryTest {

    @Test
    void awaitReceivesCompletion() throws Exception {
        var registry = new OrchestratorRunRegistry();
        var runId = UUID.randomUUID();
        var conversationId = UUID.randomUUID();
        registry.register(runId, conversationId);

        var pool = Executors.newSingleThreadExecutor();
        try {
            var future =
                    pool.submit(
                            () ->
                                    registry.await(
                                            runId, 5));
            Thread.sleep(100);
            registry.complete(runId, new RunCompleteRequest("completed", "hello", null, null, null, null));
            assertThat(future.get(5, TimeUnit.SECONDS).reply()).isEqualTo("hello");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void unknownRunIdThrows() {
        var registry = new OrchestratorRunRegistry();
        assertThatThrownBy(() -> registry.conversationIdFor(UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void conversationIdForRegisteredRun() {
        var registry = new OrchestratorRunRegistry();
        var runId = UUID.randomUUID();
        var conversationId = UUID.randomUUID();
        registry.register(runId, conversationId);
        assertThat(registry.conversationIdFor(runId)).isEqualTo(conversationId);
    }

    @Test
    void awaitTimesOutWhenNotCompleted() {
        var registry = new OrchestratorRunRegistry();
        var runId = UUID.randomUUID();
        registry.register(runId, UUID.randomUUID());
        assertThatThrownBy(() -> registry.await(runId, 0))
                .isInstanceOf(TimeoutException.class);
    }

    @Test
    void discardRemovesPendingRun() {
        var registry = new OrchestratorRunRegistry();
        var runId = UUID.randomUUID();
        registry.register(runId, UUID.randomUUID());
        registry.discard(runId);
        assertThatThrownBy(() -> registry.conversationIdFor(runId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void completeUnknownRunThrows() {
        var registry = new OrchestratorRunRegistry();
        assertThatThrownBy(
                        () ->
                                registry.complete(
                                        UUID.randomUUID(),
                                        new RunCompleteRequest("failed", null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

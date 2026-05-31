package com.couragegang.ai.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.couragegang.ai.metrics.OutboundHttpMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Covers evaluate() with toolArguments payload (PolicyClient is not JaCoCo-excluded). */
class PolicyClientEvaluateArgsTest {

    @Test
    void evaluateWhenDisabledReturnsAllow() {
        var client =
                new PolicyClient(
                        false, "http://localhost:8085/v1/policy", "key", new OutboundHttpMetrics(new SimpleMeterRegistry()));
        var res =
                client.evaluate(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "notion",
                        "notion_edit_block",
                        UUID.randomUUID(),
                        Map.of("find_text", "a", "new_text", "b"));
        assertThat(res).isPresent();
        assertThat(res.get().decision()).isEqualTo("allow");
    }
}

package com.couragegang.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

/** ADR-003 phase D: heuristics removed; explicit toolName only via ChatRequest. */
class ToolIntentResolverTest {

    private final ToolIntentResolver resolver = new ToolIntentResolver();

    @Test
    void resolveAlwaysEmpty() {
        assertThat(resolver.resolve("сохрани в notion", Set.of("notion"))).isEmpty();
        assertThat(resolver.resolve("замени old на new", Set.of("notion"))).isEmpty();
        assertThat(resolver.resolve("поиск notion", Set.of("notion"))).isEmpty();
        assertThat(resolver.resolve(null, Set.of("notion"))).isEmpty();
        assertThat(resolver.resolve("  ", Set.of("notion"))).isEmpty();
    }
}

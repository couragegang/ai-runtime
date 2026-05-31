package com.couragegang.ai.service;

import jakarta.inject.Singleton;
import java.util.Optional;
import java.util.Set;

/**
 * @deprecated ADR-003 phase D: L1 routing uses {@link OrchestratorRouterService} / n8n connector
 *     workflows. Direct chat no longer infers Notion tools from message heuristics.
 */
@Deprecated
@Singleton
public final class ToolIntentResolver {

    public record ResolvedTool(String connectorKey, String toolName) {}

    public Optional<ResolvedTool> resolve(String message, Set<String> activeConnectors) {
        return Optional.empty();
    }
}

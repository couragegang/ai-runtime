package com.couragegang.ai.service;

import com.couragegang.ai.integration.McpToolClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Singleton;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Предпросмотр notion_edit_block через MCP (preview=true) для HITL. */
@Singleton
public final class NotionEditPreviewEnricher {

    private static final Logger LOG = LoggerFactory.getLogger(NotionEditPreviewEnricher.class);
    private static final String PAYLOAD_MARKER = "---notion_edit_preview---";

    private final McpToolClient mcp;
    private final ObjectMapper json;

    public NotionEditPreviewEnricher(McpToolClient mcp) {
        this.mcp = mcp;
        this.json = new ObjectMapper();
    }

    public Map<String, Object> enrichForHitl(UUID workspaceId, String toolName, Map<String, Object> arguments) {
        if (workspaceId == null || arguments == null || arguments.isEmpty()) {
            return arguments != null ? arguments : Map.of();
        }
        var normalized = toolName != null ? toolName.toLowerCase(Locale.ROOT) : "";
        if (!normalized.contains("edit")) {
            return arguments;
        }
        if (hasPreviewFields(arguments)) {
            return arguments;
        }
        var findText = firstNonBlank(arguments, "find_text", "old_text", "search_text");
        var newText = firstNonBlank(arguments, "new_text", "replace_with", "replacement", "content");
        if (findText == null || newText == null) {
            return arguments;
        }
        var previewArgs = new LinkedHashMap<>(arguments);
        previewArgs.put("preview", true);
        var result = mcp.invoke(workspaceId, "notion", "notion_edit_block", previewArgs);
        if (result.isEmpty() || !result.get().ok()) {
            return arguments;
        }
        var merged = mergeFromSummary(arguments, result.get().summary());
        return merged != null ? merged : arguments;
    }

    private static boolean hasPreviewFields(Map<String, Object> args) {
        return firstNonBlank(args, "block_before", "block_after") != null
                && firstNonBlank(args, "block_id") != null;
    }

    private Map<String, Object> mergeFromSummary(Map<String, Object> base, String summary) {
        if (summary == null || !summary.contains(PAYLOAD_MARKER)) {
            return null;
        }
        var idx = summary.indexOf(PAYLOAD_MARKER);
        var payload = summary.substring(idx + PAYLOAD_MARKER.length()).trim();
        try {
            JsonNode node = json.readTree(payload);
            var out = new LinkedHashMap<>(base);
            putIfPresent(out, "block_id", node.path("block_id").asText(null));
            putIfPresent(out, "block_before", node.path("block_before").asText(null));
            putIfPresent(out, "block_after", node.path("block_after").asText(null));
            putIfPresent(out, "page_url", node.path("page_url").asText(null));
            out.remove("preview");
            return out;
        } catch (Exception e) {
            LOG.warn("notion edit preview payload parse failed: {}", e.toString());
            return null;
        }
    }

    private static void putIfPresent(Map<String, Object> out, String key, String value) {
        if (value != null && !value.isBlank()) {
            out.put(key, value);
        }
    }

    private static String firstNonBlank(Map<String, Object> args, String... keys) {
        for (var key : keys) {
            var v = args.get(key);
            if (v != null && !String.valueOf(v).isBlank()) {
                return String.valueOf(v).strip();
            }
        }
        return null;
    }
}

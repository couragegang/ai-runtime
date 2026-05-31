package com.couragegang.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.couragegang.ai.integration.McpToolClient;
import com.couragegang.ai.integration.McpToolClient.InvokeResult;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotionEditPreviewEnricherTest {

    @Mock
    McpToolClient mcp;

    NotionEditPreviewEnricher enricher;
    UUID wsId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        enricher = new NotionEditPreviewEnricher(mcp);
    }

    @Test
    void returnsBaseWhenNotEditTool() {
        var base = Map.<String, Object>of("content", "x");
        assertThat(enricher.enrichForHitl(wsId, "notion_write_page", base)).isEqualTo(base);
    }

    @Test
    void returnsBaseWhenToolNameNull() {
        var args = Map.<String, Object>of("find_text", "a", "new_text", "b");
        assertThat(enricher.enrichForHitl(wsId, null, args)).isEqualTo(args);
    }

    @Test
    void returnsBaseWhenPreviewFieldsAlreadyPresent() {
        var base =
                Map.<String, Object>of(
                        "block_id", "b1",
                        "block_before", "old",
                        "block_after", "new",
                        "find_text", "old");
        assertThat(enricher.enrichForHitl(wsId, "notion_edit_block", base)).isEqualTo(base);
    }

    @Test
    void mergesPayloadFromMcpPreviewSummary() {
        var args = new LinkedHashMap<String, Object>();
        args.put("find_text", "old");
        args.put("new_text", "new");
        var summary =
                "Preview\n---notion_edit_preview---"
                        + "{\"block_id\":\"blk\",\"block_before\":\"old\",\"block_after\":\"new\",\"page_url\":\"https://n/p\"}";
        when(mcp.invoke(eq(wsId), eq("notion"), eq("notion_edit_block"), any()))
                .thenReturn(Optional.of(InvokeResult.success(summary)));

        var out = enricher.enrichForHitl(wsId, "notion_edit_block", args);
        assertThat(out.get("block_id")).isEqualTo("blk");
        assertThat(out.get("block_before")).isEqualTo("old");
        assertThat(out.get("page_url")).isEqualTo("https://n/p");
        assertThat(out).doesNotContainKey("preview");
    }

    @Test
    void returnsBaseWhenMcpPreviewFails() {
        var args = Map.<String, Object>of("find_text", "a", "new_text", "b");
        when(mcp.invoke(any(), any(), any(), any())).thenReturn(Optional.of(InvokeResult.failure("err")));
        assertThat(enricher.enrichForHitl(wsId, "notion_edit_block", args)).isEqualTo(args);
    }

    @Test
    void returnsBaseWhenWorkspaceOrArgsMissing() {
        var args = Map.<String, Object>of("find_text", "a", "new_text", "b");
        assertThat(enricher.enrichForHitl(null, "notion_edit_block", args)).isEqualTo(args);
        assertThat(enricher.enrichForHitl(wsId, "notion_edit_block", Map.of())).isEqualTo(Map.of());
    }

    @Test
    void returnsBaseWhenFindOrNewTextMissing() {
        var partial = Map.<String, Object>of("find_text", "only");
        assertThat(enricher.enrichForHitl(wsId, "notion_edit_block", partial)).isEqualTo(partial);
    }

    @Test
    void returnsBaseOnInvalidPayloadJson() {
        var args = Map.<String, Object>of("find_text", "a", "new_text", "b");
        when(mcp.invoke(any(), any(), any(), any()))
                .thenReturn(Optional.of(InvokeResult.success("---notion_edit_preview---{not-json")));
        assertThat(enricher.enrichForHitl(wsId, "notion_edit_block", args)).isEqualTo(args);
    }

    @Test
    void returnsBaseWhenSummaryHasNoPayloadMarker() {
        var args = Map.<String, Object>of("find_text", "a", "new_text", "b");
        when(mcp.invoke(any(), any(), any(), any()))
                .thenReturn(Optional.of(InvokeResult.success("no marker here")));
        assertThat(enricher.enrichForHitl(wsId, "notion_edit_block", args)).isEqualTo(args);
    }
}

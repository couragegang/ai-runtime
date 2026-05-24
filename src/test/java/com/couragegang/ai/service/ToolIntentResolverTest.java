package com.couragegang.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ToolIntentResolverTest {

    private final ToolIntentResolver resolver = new ToolIntentResolver();

    @Test
    void detectsNotionWrite() {
        var r = resolver.resolve("сохрани это в notion", Set.of("notion"));
        assertThat(r).isPresent();
        assertThat(r.get().toolName()).isEqualTo("notion_write_page");
    }

    @Test
    void detectsWriteKeywordSohrani() {
        assertThat(resolver.resolve("сохрани в notion заметку", Set.of("notion")).orElseThrow().toolName())
                .isEqualTo("notion_write_page");
    }

    @Test
    void detectsSearchKeywords() {
        assertThat(resolver.resolve("поиск notion", Set.of("notion")).orElseThrow().toolName())
                .isEqualTo("notion_search");
        assertThat(resolver.resolve("search notion tasks", Set.of("notion")).orElseThrow().toolName())
                .isEqualTo("notion_search");
        assertThat(resolver.resolve("fetch notion page", Set.of("notion")).orElseThrow().toolName())
                .isEqualTo("notion_search");
    }

    @Test
    void detectsNotionViaCyrillicName() {
        assertThat(resolver.resolve("сохрани в ноушен", Set.of("notion"))).isPresent();
        assertThat(resolver.resolve("сохрани в ношен", Set.of("notion"))).isPresent();
    }

    @Test
    void notionOnlyConnectorWithoutExplicitMention() {
        var r = resolver.resolve("сохрани заметку", Set.of("notion"));
        assertThat(r).isPresent();
        assertThat(r.get().toolName()).isEqualTo("notion_write_page");
    }

    @Test
    void emptyWhenNotionNotInstalled() {
        assertThat(resolver.resolve("сохрани в notion", Set.of())).isEmpty();
    }

    @Test
    void emptyWhenOtherConnectorOnly() {
        assertThat(resolver.resolve("сохрани в notion", Set.of("slack"))).isEmpty();
    }

    @Test
    void emptyForBlankMessage() {
        assertThat(resolver.resolve("  ", Set.of("notion"))).isEmpty();
        assertThat(resolver.resolve(null, Set.of("notion"))).isEmpty();
    }

    @Test
    void emptyWhenNoIntentKeywords() {
        assertThat(resolver.resolve("привет notion", Set.of("notion"))).isEmpty();
    }

    @Test
    void detectsListPagesAsSearch() {
        assertThat(
                        resolver.resolve(
                                        "Какие страницы есть у меня в пространстве Notion?", Set.of("notion"))
                                .orElseThrow()
                                .toolName())
                .isEqualTo("notion_search");
    }
}

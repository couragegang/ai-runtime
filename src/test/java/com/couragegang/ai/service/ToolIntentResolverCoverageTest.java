package com.couragegang.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

/** Доп. кейсы для покрытия эвристик ToolIntentResolver (ветки OR в matchesWrite/Search). */
class ToolIntentResolverCoverageTest {

    private final ToolIntentResolver resolver = new ToolIntentResolver();

    @Test
    void writeBranches() {
        assertThat(resolver.resolve("добавь в notion", Set.of("notion"))).isPresent();
        assertThat(resolver.resolve("write notion page", Set.of("notion"))).isPresent();
        assertThat(resolver.resolve("create in notion", Set.of("notion"))).isPresent();
        assertThat(resolver.resolve("update notion", Set.of("notion"))).isPresent();
        assertThat(resolver.resolve("добавь заметку notion", Set.of("notion"))).isPresent();
    }

    @Test
    void searchBranches() {
        assertThat(resolver.resolve("найди notion doc", Set.of("notion")).orElseThrow().toolName())
                .isEqualTo("notion_search");
        assertThat(resolver.resolve("find notion", Set.of("notion")).orElseThrow().toolName())
                .isEqualTo("notion_search");
        assertThat(resolver.resolve("прочитай notion", Set.of("notion")).orElseThrow().toolName())
                .isEqualTo("notion_search");
        assertThat(resolver.resolve("покажи notion", Set.of("notion")).orElseThrow().toolName())
                .isEqualTo("notion_search");
        assertThat(resolver.resolve("fetch from notion", Set.of("notion")).orElseThrow().toolName())
                .isEqualTo("notion_search");
    }

    @Test
    void listIntentBranches() {
        assertThat(resolver.resolve("какой страниц в notion", Set.of("notion")).orElseThrow().toolName())
                .isEqualTo("notion_search");
        assertThat(resolver.resolve("что есть страниц в notion", Set.of("notion")).orElseThrow().toolName())
                .isEqualTo("notion_search");
        assertThat(resolver.resolve("список страниц notion", Set.of("notion")).orElseThrow().toolName())
                .isEqualTo("notion_search");
        assertThat(resolver.resolve("перечисли страниц notion", Set.of("notion")).orElseThrow().toolName())
                .isEqualTo("notion_search");
        assertThat(resolver.resolve("show notion page", Set.of("notion")).orElseThrow().toolName())
                .isEqualTo("notion_search");
    }

    @Test
    void searchFollowUpBranches() {
        assertThat(resolver.resolve("название", Set.of("notion")).orElseThrow().toolName())
                .isEqualTo("notion_search");
        assertThat(resolver.resolve("тема notion", Set.of("notion")).orElseThrow().toolName())
                .isEqualTo("notion_search");
        assertThat(resolver.resolve("topic", Set.of("notion")).orElseThrow().toolName())
                .isEqualTo("notion_search");
        assertThat(resolver.resolve("name", Set.of("notion")).orElseThrow().toolName())
                .isEqualTo("notion_search");
        assertThat(resolver.resolve("страниц", Set.of("notion")).orElseThrow().toolName())
                .isEqualTo("notion_search");
    }

    @Test
    void searchFollowUpTooLongReturnsEmpty() {
        assertThat(resolver.resolve("название " + "x".repeat(50), Set.of("notion"))).isEmpty();
    }

    @Test
    void emptyWhenConnectorsNull() {
        assertThat(resolver.resolve("сохрани в notion", null)).isEmpty();
    }

    @Test
    void writeZapisKeyword() {
        assertThat(resolver.resolve("записать в notion", Set.of("notion")).orElseThrow().toolName())
                .isEqualTo("notion_write_page");
    }
}

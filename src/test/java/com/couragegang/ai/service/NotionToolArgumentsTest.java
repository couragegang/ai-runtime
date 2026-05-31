package com.couragegang.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NotionToolArgumentsTest {

    @Test
    void forApprovedPrefersStoredArguments() {
        var stored =
                Map.<String, Object>of(
                        "title", "Обед",
                        "content", "Пельмени — было вкусно",
                        "message", "Пельмени — было вкусно");
        var args =
                NotionToolArguments.forApproved(
                        "notion_write_page",
                        stored,
                        List.of(new ChatTurn("user", "запиши в notion")),
                        "ignored fallback");
        assertThat(args).isEqualTo(stored);
    }

    @Test
    void writeUsesPageTitleHintWhenNotCreatingNew() {
        var args =
                NotionToolArguments.forTool(
                        "notion_write_page",
                        List.of(
                                new ChatTurn(
                                        "user",
                                        "Добавь на страницу Ideas: пельмени были вкусно")),
                        null);
        assertThat(args.get("create_new")).isEqualTo(false);
        assertThat(args.get("page_title")).isEqualTo("Ideas");
        assertThat(String.valueOf(args.get("content"))).contains("пельмени");
    }

    @Test
    void writeSetsCreateNewWhenUserAsksForNewPage() {
        var args =
                NotionToolArguments.forTool(
                        "notion_write_page",
                        List.of(new ChatTurn("user", "создай новую страницу Meeting notes")),
                        null);
        assertThat(args.get("create_new")).isEqualTo(true);
        assertThat(args.get("title")).isEqualTo("Meeting notes");
    }

    @Test
    void searchUsesLastUserQuery() {
        var args =
                NotionToolArguments.forTool(
                        "notion_search",
                        List.of(new ChatTurn("user", "какие страницы в notion")),
                        "assistant junk with создай");
        assertThat(args.get("query")).isEqualTo("какие страницы в notion");
    }
}

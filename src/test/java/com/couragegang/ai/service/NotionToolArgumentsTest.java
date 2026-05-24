package com.couragegang.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class NotionToolArgumentsTest {

    @Test
    void writeUsesLastUserMessageAsTitle() {
        var args =
                NotionToolArguments.forTool(
                        "notion_write_page",
                        List.of(
                                new ChatTurn("user", "привет"),
                                new ChatTurn(
                                        "user",
                                        "Запиши в ноушен что я похавал пельмени было вкусно")),
                        null);
        assertThat(args.get("title"))
                .isEqualTo("что я похавал пельмени было вкусно");
        assertThat(String.valueOf(args.get("content"))).contains("пельмени");
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

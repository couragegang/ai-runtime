package com.couragegang.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.couragegang.ai.service.OrchestratorToolCatalog.ToolDefinition;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HitlPromptFormatterTest {

    @Test
    void formatsWriteApprovalWithTitleAndContent() {
        var tool =
                new ToolDefinition(
                        "notion",
                        "Notion",
                        "notion_write_page",
                        "Запись страницы",
                        "desc",
                        true);
        var msg =
                HitlPromptFormatter.formatApprovalRequired(
                        tool,
                        Map.of("page_title", "Roadmap", "content", "Текст заметки"),
                        1,
                        2);
        assertThat(msg).contains("Требуется ваше решение");
        assertThat(msg).contains("добавлен текст");
        assertThat(msg).contains("Roadmap");
        assertThat(msg).contains("Подтвердить");
    }

    @Test
    void formatsEditBlockApprovalWithBeforeAfter() {
        var tool =
                new ToolDefinition(
                        "notion",
                        "Notion",
                        "notion_edit_block",
                        "Правка блока",
                        "desc",
                        true);
        var msg =
                HitlPromptFormatter.formatApprovalRequired(
                        tool,
                        Map.of(
                                "page_title",
                                "Ideas",
                                "block_before",
                                "Пельмени — было вкусно",
                                "block_after",
                                "Пельмени — очень вкусно"),
                        1,
                        1);
        assertThat(msg).contains("изменён фрагмент");
        assertThat(msg).contains("было вкусно");
        assertThat(msg).contains("очень вкусно");
    }
}

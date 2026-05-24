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
                        tool, Map.of("title", "Идеи", "content", "Текст заметки"), 1, 2);
        assertThat(msg).contains("Требуется ваше решение");
        assertThat(msg).contains("Notion");
        assertThat(msg).contains("Идеи");
        assertThat(msg).contains("Подтвердить");
    }
}

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

    @Test
    void formatsPlanApprovalWithNumberedSteps() {
        var steps =
                java.util.List.of(
                        new com.couragegang.ai.api.dto.OrchestratorDtos.PlanStep(
                                "notion", null, null, null, "Обновить Ideas в Notion", null, null),
                        new com.couragegang.ai.api.dto.OrchestratorDtos.PlanStep(
                                "trello", null, null, null, "Создать карточку в Trello", null, null));
        var msg =
                HitlPromptFormatter.formatPlanApprovalRequired(
                        steps,
                        "Сначала Notion, затем Trello",
                        java.util.List.of(
                                new OrchestratorToolCatalog.ConnectorCapability(
                                        "notion", "Notion", "desc"),
                                new OrchestratorToolCatalog.ConnectorCapability(
                                        "trello", "Trello", "desc")));
        assertThat(msg).contains("подтверждение плана");
        assertThat(msg).contains("1.");
        assertThat(msg).contains("Подтвердить план");
        assertThat(msg).contains("Ideas");
    }
}

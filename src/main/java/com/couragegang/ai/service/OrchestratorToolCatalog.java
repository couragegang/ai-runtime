package com.couragegang.ai.service;

import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Singleton
public final class OrchestratorToolCatalog {

    public record ToolDefinition(
            String connectorKey,
            String connectorDisplayName,
            String toolName,
            String displayName,
            String description,
            boolean writeLike) {}

    public List<ToolDefinition> toolsForConnectors(Set<String> connectorKeys) {
        var out = new ArrayList<ToolDefinition>();
        if (connectorKeys == null) {
            return out;
        }
        for (var key : connectorKeys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            switch (key.trim().toLowerCase(Locale.ROOT)) {
                case "notion" -> {
                    out.add(
                            new ToolDefinition(
                                    "notion",
                                    "Notion",
                                    "notion_search",
                                    "Поиск страниц",
                                    "Ищет страницы и блоки в подключённом Notion workspace по текстовому запросу.",
                                    false));
                    out.add(
                            new ToolDefinition(
                                    "notion",
                                    "Notion",
                                    "notion_write_page",
                                    "Запись страницы",
                                    "Создаёт новую страницу или добавляет текст на существующую (по page_title/page_url; create_new=true только для новой).",
                                    true));
                    out.add(
                            new ToolDefinition(
                                    "notion",
                                    "Notion",
                                    "notion_edit_block",
                                    "Правка блока",
                                    "Заменяет фразу в существующем текстовом блоке страницы (find_text → new_text; page_title/page_url).",
                                    true));
                }
                default -> {}
            }
        }
        return out;
    }

    public ToolDefinition find(String connectorKey, String toolName) {
        if (connectorKey == null || toolName == null) {
            return null;
        }
        return toolsForConnectors(Set.of(connectorKey)).stream()
                .filter(t -> t.toolName().equals(toolName))
                .findFirst()
                .orElse(null);
    }
}

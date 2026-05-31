package com.couragegang.ai.service;

import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Singleton
public final class OrchestratorToolCatalog {

    /** Connectors with L2 n8n workflow — L1 router must not plan toolName for these. */
    private static final Set<String> CONNECTOR_WORKFLOW_KEYS = Set.of("notion", "trello");

    public record ToolDefinition(
            String connectorKey,
            String connectorDisplayName,
            String toolName,
            String displayName,
            String description,
            boolean writeLike) {}

    /** L1 router: connector-level capabilities (delegates tool choice to connector workflow). */
    public record ConnectorCapability(
            String connectorKey, String displayName, String description) {}

    public List<ConnectorCapability> connectorCapabilities(Set<String> connectorKeys) {
        var out = new ArrayList<ConnectorCapability>();
        if (connectorKeys == null) {
            return out;
        }
        for (var key : connectorKeys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            switch (key.trim().toLowerCase(Locale.ROOT)) {
                case "notion" ->
                        out.add(
                                new ConnectorCapability(
                                        "notion",
                                        "Notion",
                                        "Поиск, запись и правка страниц в подключённом Notion workspace (внутренняя цепочка search/write/edit)."));
                case "trello" ->
                        out.add(
                                new ConnectorCapability(
                                        "trello",
                                        "Trello",
                                        "Карточки, колонки и комментарии на досках Trello через mcp-trello."));
                default -> {}
            }
        }
        return out;
    }

    public static boolean hasConnectorWorkflow(String connectorKey) {
        if (connectorKey == null || connectorKey.isBlank()) {
            return false;
        }
        return CONNECTOR_WORKFLOW_KEYS.contains(connectorKey.trim().toLowerCase(Locale.ROOT));
    }

    /** Tools exposed to L1 LLM router (empty for delegated connectors). */
    public List<ToolDefinition> toolsForRouter(Set<String> connectorKeys) {
        return toolsForConnectors(connectorKeys).stream()
                .filter(t -> !hasConnectorWorkflow(t.connectorKey()))
                .toList();
    }

    /** Full tool list for HITL labels and policy (L2/L3). */
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
                    out.add(
                            new ToolDefinition(
                                    "notion",
                                    "Notion",
                                    "notion_delete_page",
                                    "Удаление страницы",
                                    "Перемещает страницу в корзину Notion (по page_title/page_url/page_id; требует подтверждения).",
                                    true));
                }
                case "trello" -> {
                    out.add(
                            new ToolDefinition(
                                    "trello",
                                    "Trello",
                                    "trello_search_cards",
                                    "Поиск карточек",
                                    "Ищет карточки на доске Trello по запросу (board_name, query).",
                                    false));
                    out.add(
                            new ToolDefinition(
                                    "trello",
                                    "Trello",
                                    "trello_create_card",
                                    "Создать карточку",
                                    "Создаёт карточку на доске (board_name, list_name, name, desc).",
                                    true));
                    out.add(
                            new ToolDefinition(
                                    "trello",
                                    "Trello",
                                    "trello_add_comment",
                                    "Комментарий к карточке",
                                    "Добавляет комментарий к существующей карточке (name/card_id, desc).",
                                    true));
                    out.add(
                            new ToolDefinition(
                                    "trello",
                                    "Trello",
                                    "trello_list_lists",
                                    "Список колонок",
                                    "Показывает колонки (списки) на доске Trello (board_name).",
                                    false));
                    out.add(
                            new ToolDefinition(
                                    "trello",
                                    "Trello",
                                    "trello_create_list",
                                    "Создать колонку",
                                    "Создаёт колонку на доске (board_name, list_name).",
                                    true));
                    out.add(
                            new ToolDefinition(
                                    "trello",
                                    "Trello",
                                    "trello_rename_list",
                                    "Переименовать колонку",
                                    "Переименовывает колонку (board_name, list_name, new_name).",
                                    true));
                    out.add(
                            new ToolDefinition(
                                    "trello",
                                    "Trello",
                                    "trello_archive_list",
                                    "Архивировать колонку",
                                    "Архивирует колонку на доске (board_name, list_name).",
                                    true));
                    out.add(
                            new ToolDefinition(
                                    "trello",
                                    "Trello",
                                    "trello_move_card",
                                    "Переместить карточку",
                                    "Перемещает карточку в другую колонку (name/card_id, list_name, board_name).",
                                    true));
                    out.add(
                            new ToolDefinition(
                                    "trello",
                                    "Trello",
                                    "trello_delete_card",
                                    "Удалить карточку",
                                    "Удаляет карточку из Trello (name/card_id).",
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

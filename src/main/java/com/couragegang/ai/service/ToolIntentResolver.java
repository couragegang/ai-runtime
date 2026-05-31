package com.couragegang.ai.service;

import jakarta.inject.Singleton;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Singleton
public final class ToolIntentResolver {

    public record ResolvedTool(String connectorKey, String toolName) {}

    public Optional<ResolvedTool> resolve(String message, Set<String> activeConnectors) {
        if (message == null || message.isBlank() || activeConnectors == null || activeConnectors.isEmpty()) {
            return Optional.empty();
        }
        if (!activeConnectors.contains("notion")) {
            return Optional.empty();
        }
        var lower = message.toLowerCase(Locale.ROOT);
        var notionOnly = activeConnectors.size() == 1 && activeConnectors.contains("notion");
        if (!mentionsNotion(lower) && !notionOnly) {
            return Optional.empty();
        }
        if (matchesListIntent(lower) || matchesSearchIntent(lower) || matchesSearchFollowUp(lower)) {
            return Optional.of(new ResolvedTool("notion", "notion_search"));
        }
        if (matchesEditBlockIntent(lower)) {
            return Optional.of(new ResolvedTool("notion", "notion_edit_block"));
        }
        if (matchesWriteIntent(lower)) {
            return Optional.of(new ResolvedTool("notion", "notion_write_page"));
        }
        return Optional.empty();
    }

    private static boolean mentionsNotion(String lower) {
        return lower.contains("notion") || lower.contains("ноушен") || lower.contains("ношен");
    }

    private static boolean matchesEditBlockIntent(String lower) {
        return lower.contains("замени")
                || lower.contains("исправ")
                || lower.contains("отредакт")
                || lower.contains("поменяй")
                || lower.contains("replace")
                || (lower.contains("измени") && (lower.contains("фраз") || lower.contains("текст") || lower.contains("на ")))
                || (lower.contains("change") && lower.contains("to"));
    }

    private static boolean matchesWriteIntent(String lower) {
        return lower.contains("сохран")
                || lower.contains("запис")
                || lower.contains("созда")
                || lower.contains("добав")
                || lower.contains("write")
                || lower.contains("create")
                || lower.contains("update")
                || lower.contains("добавь");
    }

    /** Короткий ответ на уточняющий вопрос ассистента о поиске. */
    private static boolean matchesSearchFollowUp(String lower) {
        if (lower.length() > 40) {
            return false;
        }
        return lower.contains("назван")
                || lower.equals("название")
                || lower.contains("тема")
                || lower.contains("topic")
                || lower.contains("name")
                || lower.contains("страниц");
    }

    private static boolean matchesListIntent(String lower) {
        return (lower.contains("какие") || lower.contains("какой") || lower.contains("что есть"))
                        && lower.contains("страниц")
                || lower.contains("список") && lower.contains("страниц")
                || lower.contains("перечисли") && lower.contains("страниц")
                || lower.contains("show") && lower.contains("page");
    }

    private static boolean matchesSearchIntent(String lower) {
        return lower.contains("найди")
                || lower.contains("поиск")
                || lower.contains("search")
                || lower.contains("find")
                || lower.contains("прочит")
                || lower.contains("покаж")
                || lower.contains("fetch");
    }
}

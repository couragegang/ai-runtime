package com.couragegang.ai.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Аргументы для MCP Notion: отдельно заголовок/тело для записи и запрос для поиска. */
final class NotionToolArguments {

    private NotionToolArguments() {}

    static Map<String, Object> forTool(String toolName, List<ChatTurn> history, String contextFallback) {
        var normalized = toolName != null ? toolName.toLowerCase(Locale.ROOT) : "";
        if (normalized.contains("write") || normalized.contains("create")) {
            return forWrite(history, contextFallback);
        }
        return forSearch(history, contextFallback);
    }

    private static Map<String, Object> forWrite(List<ChatTurn> history, String contextFallback) {
        var lastUser = lastUserContent(history);
        var title = deriveTitle(lastUser);
        var content =
                contextFallback != null && !contextFallback.isBlank()
                        ? contextFallback
                        : (lastUser != null ? lastUser : "");
        var args = new LinkedHashMap<String, Object>();
        if (title != null) {
            args.put("title", title);
        }
        args.put("content", content);
        args.put("message", content);
        return args;
    }

    private static Map<String, Object> forSearch(List<ChatTurn> history, String contextFallback) {
        var lastUser = lastUserContent(history);
        var query =
                lastUser != null && !lastUser.isBlank()
                        ? lastUser
                        : (contextFallback != null ? contextFallback : "");
        var args = new LinkedHashMap<String, Object>();
        args.put("query", query);
        args.put("q", query);
        args.put("content", query);
        args.put("message", query);
        return args;
    }

    static String deriveTitle(String lastUser) {
        if (lastUser == null || lastUser.isBlank()) {
            return null;
        }
        var t = lastUser.strip();
        var lower = t.toLowerCase(Locale.ROOT);
        for (var prefix :
                List.of(
                        "запиши в notion",
                        "запиши в ноушен",
                        "запиши в ношен",
                        "сохрани в notion",
                        "сохрани в ноушен",
                        "создай в notion",
                        "создай в ноушен",
                        "добавь в notion",
                        "добавь в ноушен",
                        "запиши",
                        "сохрани",
                        "создай страницу",
                        "создай")) {
            if (lower.startsWith(prefix)) {
                t = t.substring(prefix.length()).strip();
                if (t.startsWith(":") || t.startsWith("—") || t.startsWith("-")) {
                    t = t.substring(1).strip();
                }
                break;
            }
        }
        if (t.length() > 120) {
            var dot = t.indexOf('.');
            if (dot > 10 && dot < 120) {
                t = t.substring(0, dot).strip();
            } else {
                t = t.substring(0, 120).strip() + "…";
            }
        }
        return t.isBlank() ? null : t;
    }

    private static String lastUserContent(List<ChatTurn> history) {
        if (history == null || history.isEmpty()) {
            return null;
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            var turn = history.get(i);
            if ("user".equals(turn.role()) && turn.content() != null && !turn.content().isBlank()) {
                return turn.content().strip();
            }
        }
        return null;
    }
}

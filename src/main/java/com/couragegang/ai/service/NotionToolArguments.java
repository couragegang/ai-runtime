package com.couragegang.ai.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

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

    /** После HITL — аргументы из pending approval (то, что пользователь подтвердил). */
    static Map<String, Object> forApproved(
            String toolName,
            Map<String, Object> storedArguments,
            List<ChatTurn> history,
            String contextFallback) {
        if (storedArguments != null && !storedArguments.isEmpty() && hasStoredPayload(toolName, storedArguments)) {
            return new LinkedHashMap<>(storedArguments);
        }
        return forTool(toolName, history, contextFallback);
    }

    private static boolean hasStoredPayload(String toolName, Map<String, Object> stored) {
        var normalized = toolName != null ? toolName.toLowerCase(Locale.ROOT) : "";
        if (normalized.contains("write") || normalized.contains("create")) {
            return firstNonBlank(stored, "content", "message", "title") != null;
        }
        return firstNonBlank(stored, "query", "q", "content", "message") != null;
    }

    private static String firstNonBlank(Map<String, Object> args, String... keys) {
        for (var key : keys) {
            var v = args.get(key);
            if (v != null && !String.valueOf(v).isBlank()) {
                return String.valueOf(v).strip();
            }
        }
        return null;
    }

    private static Map<String, Object> forWrite(List<ChatTurn> history, String contextFallback) {
        var lastUser = lastUserContent(history);
        var createNew = impliesCreateNew(lastUser);
        var content =
                contextFallback != null && !contextFallback.isBlank()
                        ? contextFallback
                        : extractWriteContent(lastUser);
        var args = new LinkedHashMap<String, Object>();
        args.put("content", content != null ? content : "");
        args.put("message", content != null ? content : "");
        args.put("create_new", createNew);
        if (createNew) {
            var title = deriveTitleForNewPage(lastUser);
            if (title != null) {
                args.put("title", title);
            }
        } else {
            var pageTitle = extractPageTargetHint(lastUser);
            if (pageTitle != null) {
                args.put("page_title", pageTitle);
            } else {
                var fallbackTitle = deriveTitle(lastUser);
                if (fallbackTitle != null) {
                    args.put("page_title", fallbackTitle);
                }
            }
        }
        return args;
    }

    private static boolean impliesCreateNew(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        var lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("новую страницу")
                || lower.contains("новая страница")
                || lower.contains("create new")
                || lower.contains("new page")
                || lower.contains("создай новую")
                || lower.contains("создать новую");
    }

    private static String extractPageTargetHint(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        var patterns =
                List.of(
                        Pattern.compile(
                                "(?:на|в)\\s+страниц(?:у|е)\\s+[«\"']?([^«\"'\\n,.:;]+)",
                                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                        Pattern.compile(
                                "(?:добавь|запиши|допиши|обнови|внеси)\\s+(?:в|на)\\s+[«\"']?([^«\"'\\n,.:;]+)",
                                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                        Pattern.compile(
                                "(?:to|on|into)\\s+(?:page|note)\\s+[«\"']?([^«\"'\\n,.]+)",
                                Pattern.CASE_INSENSITIVE));
        for (var pattern : patterns) {
            var matcher = pattern.matcher(message.strip());
            if (matcher.find()) {
                var hint = matcher.group(1).strip();
                if (!hint.isBlank() && hint.length() <= 120) {
                    return hint;
                }
            }
        }
        return null;
    }

    private static String extractWriteContent(String lastUser) {
        if (lastUser == null || lastUser.isBlank()) {
            return "";
        }
        var hint = extractPageTargetHint(lastUser);
        if (hint != null) {
            var colon = lastUser.indexOf(':');
            if (colon >= 0) {
                var after = lastUser.substring(colon + 1).strip();
                if (!after.isBlank()) {
                    return after;
                }
            }
        }
        return lastUser.strip();
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
        var t = new StringBuilder(lastUser.strip());
        var lower = t.toString().toLowerCase(Locale.ROOT);
        var prefixes =
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
                        "создай страницу",
                        "запиши",
                        "сохрани",
                        "создай");
        prefixes.stream()
                .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                .filter(lower::startsWith)
                .findFirst()
                .ifPresent(
                        prefix -> {
                            var stripped = t.substring(prefix.length()).strip();
                            if (stripped.startsWith(":") || stripped.startsWith("—") || stripped.startsWith("-")) {
                                stripped = stripped.substring(1).strip();
                            }
                            t.setLength(0);
                            t.append(stripped);
                        });
        var result = t.toString();
        if (result.length() > 120) {
            var dot = result.indexOf('.');
            if (dot > 10 && dot < 120) {
                result = result.substring(0, dot).strip();
            } else {
                result = result.substring(0, 120).strip() + "…";
            }
        }
        return result.isBlank() ? null : result;
    }

    private static String deriveTitleForNewPage(String lastUser) {
        var title = deriveTitle(lastUser);
        if (title == null) {
            return null;
        }
        var lower = title.toLowerCase(Locale.ROOT);
        for (var prefix : List.of("новую страницу", "новая страница", "new page")) {
            if (lower.startsWith(prefix)) {
                var stripped = title.substring(prefix.length()).strip();
                return stripped.isBlank() ? null : stripped;
            }
        }
        return title;
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

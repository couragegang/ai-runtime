package com.couragegang.ai.service;

import com.couragegang.ai.service.OrchestratorToolCatalog.ToolDefinition;
import java.util.Locale;
import java.util.Map;

public final class HitlPromptFormatter {

    private HitlPromptFormatter() {}

    public static String formatApprovalRequired(
            ToolDefinition tool, Map<String, Object> arguments, int stepIndex, int totalSteps) {
        var stepLabel =
                totalSteps > 1
                        ? "Шаг " + stepIndex + " из " + totalSteps + "\n\n"
                        : "";
        var action = describeAction(tool, arguments);
        return stepLabel
                + "**Требуется ваше решение**\n\n"
                + "**Коннектор:** "
                + (tool != null ? tool.connectorDisplayName() : "—")
                + "\n"
                + "**Инструмент:** "
                + (tool != null ? tool.displayName() + " (`" + tool.toolName() + "`)" : "—")
                + "\n\n"
                + "**Что будет выполнено:**\n"
                + action
                + "\n\n"
                + "Нажмите **«Подтвердить»**, чтобы выполнить это действие, или **«Отклонить»**, чтобы отменить.";
    }

    public static String formatDenied(ToolDefinition tool) {
        return "**Действие отклонено политикой**\n\n"
                + (tool != null
                        ? "Инструмент **" + tool.displayName() + "** (`" + tool.toolName() + "`) запрещён правилами workspace."
                        : "Вызов инструмента запрещён правилами workspace.");
    }

    private static String describeAction(ToolDefinition tool, Map<String, Object> arguments) {
        if (tool == null) {
            return formatArgs(arguments);
        }
        var name = tool.toolName().toLowerCase(Locale.ROOT);
        if (name.contains("edit")) {
            return describeEditBlockAction(arguments);
        }
        if (name.contains("write") || name.contains("create")) {
            var createNew = isTruthy(arguments, "create_new");
            var pageTitle = firstNonBlank(arguments, "page_title", "target_page");
            var pageRef = firstNonBlank(arguments, "page_id", "page_url");
            var title = stringArg(arguments, "title");
            var content = firstNonBlank(arguments, "content", "message");
            var sb = new StringBuilder();
            if (createNew) {
                sb.append("Будет **создана новая** страница в Notion.");
                if (title != null) {
                    sb.append("\n- **Заголовок:** ").append(title);
                }
            } else {
                sb.append("Будет **добавлен текст** на существующую страницу Notion.");
                if (pageRef != null) {
                    if (pageRef.startsWith("http://") || pageRef.startsWith("https://")) {
                        sb.append("\n- **Страница:** [").append(pageRef).append("](").append(pageRef).append(")");
                    } else {
                        sb.append("\n- **Страница:** ").append(pageRef);
                    }
                } else if (pageTitle != null) {
                    sb.append("\n- **Страница (поиск по названию):** ").append(pageTitle);
                } else if (title != null) {
                    sb.append("\n- **Страница (поиск по названию):** ").append(title);
                }
            }
            if (content != null) {
                var preview = content.length() > 400 ? content.substring(0, 400) + "…" : content;
                sb.append("\n- **Текст:** ").append(preview);
            }
            return sb.toString();
        }
        if (name.contains("search")) {
            var query = firstNonBlank(arguments, "query", "q", "content", "message");
            return "Будет выполнен поиск в Notion"
                    + (query != null ? " по запросу: **" + query + "**" : ".");
        }
        return tool.description() + (arguments != null && !arguments.isEmpty() ? "\n\n" + formatArgs(arguments) : "");
    }

    private static String describeEditBlockAction(Map<String, Object> arguments) {
        var pageTitle = firstNonBlank(arguments, "page_title", "target_page");
        var pageRef = firstNonBlank(arguments, "page_id", "page_url");
        var findText = firstNonBlank(arguments, "find_text", "old_text", "search_text");
        var newText = firstNonBlank(arguments, "new_text", "replace_with", "replacement", "content");
        var before = firstNonBlank(arguments, "block_before");
        var after = firstNonBlank(arguments, "block_after");
        var sb = new StringBuilder("Будет **изменён фрагмент** в блоке на странице Notion.");
        if (pageRef != null) {
            if (pageRef.startsWith("http://") || pageRef.startsWith("https://")) {
                sb.append("\n- **Страница:** [").append(pageRef).append("](").append(pageRef).append(")");
            } else {
                sb.append("\n- **Страница:** ").append(pageRef);
            }
        } else if (pageTitle != null) {
            sb.append("\n- **Страница (поиск по названию):** ").append(pageTitle);
        }
        if (before != null && after != null) {
            sb.append("\n- **Было:** ").append(truncatePreview(before));
            sb.append("\n- **Станет:** ").append(truncatePreview(after));
        } else {
            if (findText != null) {
                sb.append("\n- **Найти фразу:** ").append(truncatePreview(findText));
            }
            if (newText != null) {
                sb.append("\n- **Заменить на:** ").append(truncatePreview(newText));
            }
        }
        return sb.toString();
    }

    private static String truncatePreview(String s) {
        return s.length() > 400 ? s.substring(0, 400) + "…" : s;
    }

    private static String formatArgs(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return "Параметры не указаны.";
        }
        var sb = new StringBuilder();
        arguments.forEach(
                (k, v) -> {
                    if (v != null && !String.valueOf(v).isBlank()) {
                        sb.append("- **").append(k).append(":** ").append(v).append("\n");
                    }
                });
        return sb.toString().strip();
    }

    private static String stringArg(Map<String, Object> args, String key) {
        if (args == null) {
            return null;
        }
        var v = args.get(key);
        return v != null && !String.valueOf(v).isBlank() ? String.valueOf(v).strip() : null;
    }

    private static String firstNonBlank(Map<String, Object> args, String... keys) {
        for (var key : keys) {
            var v = stringArg(args, key);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private static boolean isTruthy(Map<String, Object> args, String key) {
        if (args == null) {
            return false;
        }
        var v = args.get(key);
        if (v instanceof Boolean b) {
            return b;
        }
        if (v == null) {
            return false;
        }
        var s = String.valueOf(v).trim().toLowerCase(Locale.ROOT);
        return "true".equals(s) || "1".equals(s);
    }
}

# AI Runtime

Оркестрация LLM и tool-calls через MCP-шлюз. Префикс **`/v1/ai`**, порт **8083**.

```bash
./gradlew run
```

## Чат и LLM

`POST /v1/ai/chat` — синхронный ответ ассистента.

| `LLM_PROVIDER` | Поведение |
|----------------|-----------|
| `stub` (по умолчанию) | Заглушка без внешнего API |
| `deepseek` | [DeepSeek API](https://api-docs.deepseek.com/) (OpenAI-совместимый `/chat/completions`) |

### DeepSeek (V4)

Задайте в окружении:

```bash
LLM_PROVIDER=deepseek
DEEPSEEK_API_KEY=sk-...
# опционально:
DEEPSEEK_MODEL=deepseek-v4-flash   # или deepseek-v4-pro
DEEPSEEK_THINKING_TYPE=disabled    # disabled | enabled
DEEPSEEK_BASE_URL=https://api.deepseek.com
```

Через BFF: `POST /v1/bff/api/chat` с JWT (как в E2E).

## Policy

Перед вызовом LLM для tool-call с `orgId` + `workspaceId` + `toolName` запрашивается `policy-service` (см. `POLICY_SERVICE_*`).

## Audit

После каждого `POST /chat` с `orgId` — асинхронный ingest в `audit-service` (`POST /internal/tool-events`):

| `eventType` | Когда |
|-------------|--------|
| `ai.chat` | Сообщение без tool-call |
| `ai.tool_call` | Запрос с `toolName` + org/workspace |

`outcome` = статус ответа (`stub`, `completed`, `denied`, `awaiting_approval`, `error`). Переменные: `AUDIT_SERVICE_*`, `AUDIT_INTERNAL_API_KEY`.

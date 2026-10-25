"""Запросы к Claude."""
import json

import anthropic

from .config import Settings

# Модели, для которых сервер умеет сам повторить отклонённый запрос на другой модели
FALLBACK_MODELS = {"claude-opus-5-5", "claude-opus-5", "claude-fable-5-1", "claude-sonnet-5-5"}


class BrainError(Exception):
    """Ошибка, текст которой можно показать владельцу в чате."""


class Brain:
    def __init__(self, settings: Settings):
        self.settings = settings
        self.client = anthropic.AsyncAnthropic()

    async def ask(self, system: str, messages: list[dict], schema: dict | None = None):
        """Возвращает текст ответа, а при переданной JSON-схеме — разобранный объект."""
        output_config: dict = {"effort": self.settings.effort}
        if schema:
            output_config["format"] = {"type": "json_schema", "schema": schema}

        extra: dict = {}
        if self.settings.model in FALLBACK_MODELS:
            extra = {"betas": ["server-side-fallback-2026-07-01"], "fallbacks": "default"}

        try:
            response = await self.client.beta.messages.create(
                model=self.settings.model,
                max_tokens=16000,
                system=system,
                messages=messages,
                thinking={"type": "adaptive"},
                output_config=output_config,
                cache_control={"type": "ephemeral"},
                **extra,
            )
        except anthropic.AuthenticationError:
            raise BrainError("Ключ ANTHROPIC_API_KEY не подошёл — проверьте его в .env")
        except anthropic.PermissionDeniedError:
            raise BrainError("У ключа нет доступа к этой модели")
        except anthropic.NotFoundError:
            raise BrainError(f"Модель {self.settings.model} не найдена — проверьте CLAUDE_MODEL")
        except anthropic.RateLimitError:
            raise BrainError("Упёрлись в лимит запросов, попробуйте через минуту")
        except anthropic.BadRequestError as e:
            raise BrainError(f"Запрос отклонён: {e.message}")
        except anthropic.APIStatusError as e:
            raise BrainError(f"Сервис Claude ответил ошибкой {e.status_code}, попробуйте позже")
        except anthropic.APIConnectionError:
            raise BrainError("Нет связи с сервисом Claude")

        if response.stop_reason == "refusal":
            raise BrainError("Claude отказался выполнять этот запрос")

        text = "".join(block.text for block in response.content if block.type == "text").strip()
        if response.stop_reason == "max_tokens" and not schema:
            text += "\n\n(ответ обрезан — слишком длинный)"
        if not schema:
            return text or "…"
        try:
            return json.loads(text)
        except json.JSONDecodeError:
            raise BrainError("Не удалось разобрать план — попробуйте сформулировать задачу иначе")

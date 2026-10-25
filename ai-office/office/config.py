"""Настройки офиса: переменные окружения и список агентов из agents.toml."""
import os
import tomllib
from dataclasses import dataclass
from pathlib import Path


class ConfigError(Exception):
    pass


@dataclass(frozen=True)
class Agent:
    key: str
    name: str
    role: str
    emoji: str
    prompt: str
    token: str
    lead: bool

    @property
    def bot_id(self) -> int:
        # Токен бота начинается с его id: "123456:ABC..."
        return int(self.token.split(":", 1)[0])

    @property
    def title(self) -> str:
        return f"{self.emoji} {self.name} ({self.role})"


@dataclass(frozen=True)
class Settings:
    owner_id: int
    office_chat_id: int | None
    tasks_chat_id: int | None
    model: str
    effort: str
    db_path: Path
    history_limit: int
    max_parallel_tasks: int


def _optional_int(name: str) -> int | None:
    value = os.getenv(name, "").strip()
    return int(value) if value else None


def load_settings() -> Settings:
    owner = os.getenv("OWNER_ID", "").strip()
    if not owner:
        raise ConfigError("Не задан OWNER_ID — ваш числовой id в Telegram (узнать: @userinfobot)")
    if not (os.getenv("ANTHROPIC_API_KEY") or os.getenv("ANTHROPIC_AUTH_TOKEN")):
        raise ConfigError("Не задан ANTHROPIC_API_KEY — ключ с platform.claude.com")
    return Settings(
        owner_id=int(owner),
        office_chat_id=_optional_int("OFFICE_CHAT_ID"),
        tasks_chat_id=_optional_int("TASKS_CHAT_ID"),
        model=os.getenv("CLAUDE_MODEL", "claude-opus-5-5"),
        effort=os.getenv("CLAUDE_EFFORT", "medium"),
        db_path=Path(os.getenv("DB_PATH", "data/office.db")),
        history_limit=int(os.getenv("HISTORY_LIMIT", "30")),
        max_parallel_tasks=int(os.getenv("MAX_PARALLEL_TASKS", "3")),
    )


def load_agents(path: Path) -> list[Agent]:
    with path.open("rb") as f:
        raw = tomllib.load(f).get("agent", [])

    agents: list[Agent] = []
    for item in raw:
        token = os.getenv(item["token_env"], "").strip()
        if not token:
            print(f"[config] {item['name']}: нет токена в {item['token_env']}, агент пропущен")
            continue
        agents.append(Agent(
            key=item["key"],
            name=item["name"],
            role=item["role"],
            emoji=item.get("emoji", "🤖"),
            prompt=item["prompt"].strip(),
            token=token,
            lead=item.get("lead", False),
        ))

    if not agents:
        raise ConfigError("Ни у одного агента нет токена — заполните TOKEN_* в .env")
    leads = [a for a in agents if a.lead]
    if len(leads) > 1:
        raise ConfigError("lead = true должен быть только у одного агента")
    if not leads:
        # Без явного руководителя им становится первый агент в списке
        first = agents[0]
        agents[0] = Agent(**{**first.__dict__, "lead": True})
    return agents

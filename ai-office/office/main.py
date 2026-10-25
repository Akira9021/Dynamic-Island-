"""Точка входа: запускает всех ботов-агентов в одном процессе."""
import asyncio
import logging
import os
from pathlib import Path

from aiogram import Bot, Dispatcher, F, Router
from aiogram.enums import ChatType
from aiogram.filters import Command, CommandObject
from aiogram.types import Message

from .brain import Brain
from .config import Agent, ConfigError, load_agents, load_settings
from .db import Database
from .team import Office

HELP = """Это AI Office — команда ИИ-агентов.

Команды:
/task текст — поставить задачу команде: руководитель разобьёт её и раздаст агентам
/tasks — последние задачи и их статусы
/team — кто в команде
/reset — забыть историю личного диалога с этим агентом
/id — показать id этого чата (для настройки OFFICE_CHAT_ID и TASKS_CHAT_ID)

В личке каждый агент — отдельный собеседник. В общем чате отвечает тот,
кого вы упомянули (@бот, ответом на его сообщение или по имени в начале),
а остальное подхватывает руководитель."""


def build_router(office: Office, usernames: dict[str, str]) -> Router:
    router = Router()
    # Все агенты слушаются только владельца
    router.message.filter(F.from_user.id == office.settings.owner_id)

    def addressed(agent: Agent, message: Message) -> bool:
        text = (message.text or "").lower()
        reply = message.reply_to_message
        if reply and reply.from_user and reply.from_user.id == agent.bot_id:
            return True
        if f"@{usernames[agent.key]}".lower() in text:
            return True
        return text.startswith((agent.name.lower(), agent.key.lower()))

    def in_group(message: Message) -> bool:
        return message.chat.type in (ChatType.GROUP, ChatType.SUPERGROUP)

    @router.message(Command("start", "help"))
    async def start(message: Message, bot: Bot) -> None:
        agent = office.agent_for(bot)
        if in_group(message) and not agent.lead:
            return
        await message.answer(f"Привет! Я {agent.title}.\n\n{HELP}")

    @router.message(Command("id"))
    async def chat_id(message: Message, bot: Bot) -> None:
        if office.agent_for(bot).lead or not in_group(message):
            await message.answer(f"id этого чата: {message.chat.id}")

    @router.message(Command("team"))
    async def team(message: Message, bot: Bot) -> None:
        if office.agent_for(bot).lead or not in_group(message):
            await message.answer("Команда:\n" + "\n".join(
                f"{a.title} — @{usernames[a.key]}" + (" · руководитель" if a.lead else "")
                for a in office.agents.values()
            ))

    @router.message(Command("tasks"))
    async def tasks(message: Message, bot: Bot) -> None:
        if office.agent_for(bot).lead or not in_group(message):
            await message.answer(office.tasks_report())

    @router.message(Command("reset"))
    async def reset(message: Message, bot: Bot) -> None:
        agent = office.agent_for(bot)
        if in_group(message):
            return
        office.db.clear_history(agent.key, message.chat.id)
        await message.answer("Историю диалога забыл, начинаем с чистого листа.")

    @router.message(Command("task"))
    async def task(message: Message, bot: Bot, command: CommandObject) -> None:
        if not office.agent_for(bot).lead:
            return
        request = (command.args or "").strip()
        if not request:
            await message.answer("Напишите задачу после команды: /task сделать контент-план на неделю")
            return
        chat_id, message_id = message.chat.id, message.message_id
        if not in_group(message) and office.settings.office_chat_id:
            chat_id, message_id = office.settings.office_chat_id, None
            await message.answer("Принял, работаем в общем чате.")
        office.db.add_feed(chat_id, message.from_user.full_name, f"/task {request}", message_id)
        await office.handle_request(chat_id, request)

    @router.message(F.text & ~F.text.startswith("/"))
    async def text(message: Message, bot: Bot) -> None:
        agent = office.agent_for(bot)
        if not in_group(message):
            await office.private_chat(agent, message.chat.id, message.text)
            return

        author = message.from_user.full_name
        office.db.add_feed(message.chat.id, author, message.text, message.message_id)
        targets = [a for a in office.agents.values() if addressed(a, message)]
        if agent in targets or (not targets and agent.lead):
            await office.group_chat(agent, message.chat.id, author, message.text)

    return router


async def run() -> None:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
    root = Path(__file__).resolve().parent.parent
    try:
        settings = load_settings()
        agents = load_agents(Path(os.getenv("AGENTS_FILE", root / "agents.toml")))
    except ConfigError as e:
        raise SystemExit(f"Ошибка настройки: {e}")

    office = Office(settings, agents, Database(settings.db_path), Brain(settings))
    usernames = {}
    for key, bot in office.bots.items():
        me = await bot.me()
        usernames[key] = me.username
        logging.info("%s → @%s", office.agents[key].title, me.username)

    dp = Dispatcher()
    dp.include_router(build_router(office, usernames))
    # handle_as_tasks: пока команда работает над задачей, боты продолжают принимать сообщения
    await dp.start_polling(*office.bots.values(), handle_as_tasks=True)


def main() -> None:
    asyncio.run(run())


if __name__ == "__main__":
    main()

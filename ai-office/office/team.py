"""Логика офиса: диалоги с агентами, планирование и выполнение задач.

Telegram не пересылает ботам сообщения других ботов, поэтому «разговор»
агентов между собой ведёт этот процесс: он сам решает, кто отвечает,
и публикует ответ от имени нужного бота.
"""
import asyncio
import logging

from aiogram import Bot
from aiogram.exceptions import TelegramAPIError, TelegramForbiddenError
from aiogram.utils.chat_action import ChatActionSender

from .brain import Brain, BrainError
from .config import Agent, Settings
from .db import Database, Task

log = logging.getLogger(__name__)

TELEGRAM_LIMIT = 4000
FEED_CONTEXT = 25

COMMON_RULES = """
Ты работаешь в Telegram, поэтому пиши обычным текстом без Markdown-разметки
(без **, #, таблиц). Отвечай по-русски, если владелец не пишет на другом языке.
Будь конкретным: давай готовый результат, а не обещания его сделать.
Ты — языковая модель без доступа к интернету, файлам и внешним сервисам. Не делай
вид, что что-то отправил, запустил или проверил: если для задачи нужен доступ,
которого у тебя нет, скажи об этом и дай лучшее, что можно сделать текстом.
""".strip()


class Office:
    def __init__(self, settings: Settings, agents: list[Agent], db: Database, brain: Brain):
        self.settings = settings
        self.agents = {a.key: a for a in agents}
        self.lead = next(a for a in agents if a.lead)
        self.db = db
        self.brain = brain
        self.bots = {a.key: Bot(token=a.token) for a in agents}
        self._by_bot_id = {a.bot_id: a for a in agents}
        self._task_slots = asyncio.Semaphore(settings.max_parallel_tasks)

    def agent_for(self, bot: Bot) -> Agent:
        return self._by_bot_id[bot.id]

    def roster(self) -> str:
        return "\n".join(f"- {a.key}: {a.title}" for a in self.agents.values())

    def system_prompt(self, agent: Agent) -> str:
        return (
            f"Ты {agent.name}, {agent.role} в команде ИИ-агентов «AI Office».\n\n"
            f"{agent.prompt}\n\nКоманда:\n{self.roster()}\n\n{COMMON_RULES}"
        )

    # --- отправка ---

    async def say(self, agent: Agent, chat_id: int, text: str) -> None:
        bot, body = self.bots[agent.key], text
        try:
            await bot.send_chat_action(chat_id, "typing")
        except TelegramForbiddenError:
            # Этот бот не добавлен в чат (или владелец его не запускал) — пишет руководитель
            bot, body = self.bots[self.lead.key], f"[{agent.emoji} {agent.name}]\n{text}"
        for start in range(0, len(body), TELEGRAM_LIMIT):
            await bot.send_message(chat_id, body[start:start + TELEGRAM_LIMIT])
        self.db.add_feed(chat_id, agent.name, text)

    async def notify_tasks(self, text: str) -> None:
        if self.settings.tasks_chat_id is None:
            return
        try:
            await self.bots[self.lead.key].send_message(self.settings.tasks_chat_id, text)
        except TelegramAPIError as e:
            log.warning("Не удалось написать в чат задач: %s", e)

    def feed_text(self, chat_id: int) -> str:
        lines = [f"{author}: {text}" for author, text in self.db.get_feed(chat_id, FEED_CONTEXT)]
        return "\n".join(lines) or "(пока пусто)"

    # --- диалоги ---

    async def private_chat(self, agent: Agent, chat_id: int, text: str) -> None:
        history = self.db.get_history(agent.key, chat_id, self.settings.history_limit)
        messages = history + [{"role": "user", "content": text}]
        async with ChatActionSender.typing(bot=self.bots[agent.key], chat_id=chat_id):
            try:
                answer = await self.brain.ask(self.system_prompt(agent), messages)
            except BrainError as e:
                await self.bots[agent.key].send_message(chat_id, f"⚠️ {e}")
                return
        self.db.add_history(agent.key, chat_id, "user", text)
        self.db.add_history(agent.key, chat_id, "assistant", answer)
        bot = self.bots[agent.key]
        for start in range(0, len(answer), TELEGRAM_LIMIT):
            await bot.send_message(chat_id, answer[start:start + TELEGRAM_LIMIT])

    async def group_chat(self, agent: Agent, chat_id: int, author: str, text: str) -> None:
        """Ответ агента в общем чате. Сообщение владельца уже записано в ленту."""
        prompt = (
            f"Последние сообщения в общем чате:\n{self.feed_text(chat_id)}\n\n"
            f"Ответь на последнее сообщение от {author} как {agent.name}."
        )
        async with ChatActionSender.typing(bot=self.bots[agent.key], chat_id=chat_id):
            try:
                answer = await self.brain.ask(self.system_prompt(agent), [{"role": "user", "content": prompt}])
            except BrainError as e:
                await self.bots[agent.key].send_message(chat_id, f"⚠️ {e}")
                return
        await self.say(agent, chat_id, answer)

    # --- задачи ---

    def plan_schema(self) -> dict:
        return {
            "type": "object",
            "properties": {
                "reply": {"type": "string"},
                "tasks": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "assignee": {"type": "string", "enum": list(self.agents)},
                            "title": {"type": "string"},
                            "details": {"type": "string"},
                        },
                        "required": ["assignee", "title", "details"],
                        "additionalProperties": False,
                    },
                },
            },
            "required": ["reply", "tasks"],
            "additionalProperties": False,
        }

    async def handle_request(self, chat_id: int, request: str) -> None:
        lead = self.lead
        prompt = (
            f"Последние сообщения в общем чате:\n{self.feed_text(chat_id)}\n\n"
            f"Владелец ставит команде задачу:\n{request}\n\n"
            "Разбей её на задачи для участников команды (обычно 1–4, по одной на человека, "
            "по их ролям; себе тоже можно). В поле reply коротко напиши владельцу, "
            "как вы будете действовать. Если задача простая, можно сделать одну задачу."
        )
        async with ChatActionSender.typing(bot=self.bots[lead.key], chat_id=chat_id):
            try:
                plan = await self.brain.ask(
                    self.system_prompt(lead), [{"role": "user", "content": prompt}], schema=self.plan_schema()
                )
            except BrainError as e:
                await self.bots[lead.key].send_message(chat_id, f"⚠️ {e}")
                return

        await self.say(lead, chat_id, plan["reply"])
        tasks = [
            self.db.add_task(chat_id, request, t["assignee"], t["title"], t["details"])
            for t in plan["tasks"]
        ]
        if not tasks:
            return
        await self.notify_tasks("\n".join(
            f"📌 #{t.id} → {self.agents[t.assignee].name}: {t.title}" for t in tasks
        ))

        results = await asyncio.gather(*(self.run_task(t) for t in tasks))
        done = [(t, r) for t, r in zip(tasks, results) if r is not None]
        if len(tasks) > 1 and done:
            await self.summarize(chat_id, request, done)

    async def run_task(self, task: Task) -> str | None:
        agent = self.agents[task.assignee]
        async with self._task_slots:
            self.db.set_task_status(task.id, "in_progress")
            await self.say(agent, task.chat_id, f"Взял задачу #{task.id}: {task.title}")
            prompt = (
                f"Исходный запрос владельца:\n{task.request}\n\n"
                f"Руководитель ({self.lead.name}) поручил тебе задачу #{task.id}: {task.title}\n"
                f"Детали: {task.details}\n\n"
                "Выполни задачу и пришли готовый результат."
            )
            try:
                result = await self.brain.ask(self.system_prompt(agent), [{"role": "user", "content": prompt}])
            except BrainError as e:
                self.db.set_task_status(task.id, "failed", str(e))
                await self.say(agent, task.chat_id, f"⚠️ Не смог выполнить #{task.id}: {e}")
                await self.notify_tasks(f"❌ {agent.name} не смог выполнить #{task.id}")
                return None

        self.db.set_task_status(task.id, "done", result)
        await self.say(agent, task.chat_id, f"✅ #{task.id} {task.title}\n\n{result}")
        await self.notify_tasks(f"✅ {agent.name} закрыл #{task.id}")
        return result

    async def summarize(self, chat_id: int, request: str, done: list[tuple[Task, str]]) -> None:
        reports = "\n\n".join(
            f"#{t.id} {self.agents[t.assignee].name} — {t.title}:\n{r}" for t, r in done
        )
        prompt = (
            f"Запрос владельца:\n{request}\n\nРезультаты команды:\n{reports}\n\n"
            "Подведи короткий итог для владельца: что сделано и что делать дальше."
        )
        try:
            summary = await self.brain.ask(self.system_prompt(self.lead), [{"role": "user", "content": prompt}])
        except BrainError as e:
            log.warning("Итог не получился: %s", e)
            return
        await self.say(self.lead, chat_id, f"📋 Итог\n\n{summary}")

    def tasks_report(self) -> str:
        icons = {"todo": "⏳", "in_progress": "🔄", "done": "✅", "failed": "❌"}
        tasks = self.db.recent_tasks()
        if not tasks:
            return "Задач пока нет. Поставьте первую: /task текст задачи"
        return "\n".join(
            f"{icons.get(t.status, '•')} #{t.id} {self.agents[t.assignee].name if t.assignee in self.agents else t.assignee}: {t.title}"
            for t in tasks
        )

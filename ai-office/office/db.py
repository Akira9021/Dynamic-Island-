"""SQLite: история личных диалогов, лента общего чата и задачи."""
import sqlite3
from dataclasses import dataclass
from pathlib import Path

SCHEMA = """
CREATE TABLE IF NOT EXISTS history (
    id INTEGER PRIMARY KEY,
    agent TEXT NOT NULL,
    chat_id INTEGER NOT NULL,
    role TEXT NOT NULL,
    content TEXT NOT NULL,
    created TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS history_chat ON history(agent, chat_id, id);

CREATE TABLE IF NOT EXISTS feed (
    id INTEGER PRIMARY KEY,
    chat_id INTEGER NOT NULL,
    message_id INTEGER,
    author TEXT NOT NULL,
    text TEXT NOT NULL,
    created TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS feed_chat ON feed(chat_id, id);
-- Одно сообщение владельца получают все боты группы, а записать его нужно один раз
CREATE UNIQUE INDEX IF NOT EXISTS feed_message ON feed(chat_id, message_id);

CREATE TABLE IF NOT EXISTS tasks (
    id INTEGER PRIMARY KEY,
    chat_id INTEGER NOT NULL,
    request TEXT NOT NULL,
    assignee TEXT NOT NULL,
    title TEXT NOT NULL,
    details TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'todo',
    result TEXT,
    created TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
"""


@dataclass
class Task:
    id: int
    chat_id: int
    request: str
    assignee: str
    title: str
    details: str
    status: str
    result: str | None


class Database:
    def __init__(self, path: Path):
        path.parent.mkdir(parents=True, exist_ok=True)
        self.conn = sqlite3.connect(path)
        self.conn.executescript(SCHEMA)

    # --- личные диалоги ---

    def add_history(self, agent: str, chat_id: int, role: str, content: str) -> None:
        with self.conn:
            self.conn.execute(
                "INSERT INTO history (agent, chat_id, role, content) VALUES (?, ?, ?, ?)",
                (agent, chat_id, role, content),
            )

    def get_history(self, agent: str, chat_id: int, limit: int) -> list[dict]:
        rows = self.conn.execute(
            "SELECT role, content FROM history WHERE agent = ? AND chat_id = ? ORDER BY id DESC LIMIT ?",
            (agent, chat_id, limit),
        ).fetchall()
        messages = [{"role": r, "content": c} for r, c in reversed(rows)]
        # API требует, чтобы диалог начинался с реплики пользователя
        while messages and messages[0]["role"] != "user":
            messages.pop(0)
        return messages

    def clear_history(self, agent: str, chat_id: int) -> None:
        with self.conn:
            self.conn.execute("DELETE FROM history WHERE agent = ? AND chat_id = ?", (agent, chat_id))

    # --- общий чат ---

    def add_feed(self, chat_id: int, author: str, text: str, message_id: int | None = None) -> None:
        with self.conn:
            self.conn.execute(
                "INSERT OR IGNORE INTO feed (chat_id, message_id, author, text) VALUES (?, ?, ?, ?)",
                (chat_id, message_id, author, text),
            )

    def get_feed(self, chat_id: int, limit: int) -> list[tuple[str, str]]:
        rows = self.conn.execute(
            "SELECT author, text FROM feed WHERE chat_id = ? ORDER BY id DESC LIMIT ?",
            (chat_id, limit),
        ).fetchall()
        return list(reversed(rows))

    # --- задачи ---

    def add_task(self, chat_id: int, request: str, assignee: str, title: str, details: str) -> Task:
        with self.conn:
            cur = self.conn.execute(
                "INSERT INTO tasks (chat_id, request, assignee, title, details) VALUES (?, ?, ?, ?, ?)",
                (chat_id, request, assignee, title, details),
            )
        return Task(cur.lastrowid, chat_id, request, assignee, title, details, "todo", None)

    def set_task_status(self, task_id: int, status: str, result: str | None = None) -> None:
        with self.conn:
            self.conn.execute(
                "UPDATE tasks SET status = ?, result = COALESCE(?, result), updated = CURRENT_TIMESTAMP"
                " WHERE id = ?",
                (status, result, task_id),
            )

    def recent_tasks(self, limit: int = 15) -> list[Task]:
        rows = self.conn.execute(
            "SELECT id, chat_id, request, assignee, title, details, status, result"
            " FROM tasks ORDER BY id DESC LIMIT ?",
            (limit,),
        ).fetchall()
        return [Task(*row) for row in rows]

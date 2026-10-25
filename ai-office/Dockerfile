FROM python:3.13-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY agents.toml .
COPY office ./office
ENV PYTHONUNBUFFERED=1 DB_PATH=/app/data/office.db
CMD ["python", "-m", "office"]

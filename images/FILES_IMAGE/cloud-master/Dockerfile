FROM python:3.13-slim

WORKDIR /app

RUN apt-get update && apt-get install -y libpq-dev gcc && rm -rf /var/lib/apt/lists/*

COPY pyproject.toml .

RUN pip install --no-cache-dir \
    "paho-mqtt>=2.1.0" \
    "psycopg2>=2.9.11" \
    "redis>=7.3.0" \
    "requests>=2.32.5"

COPY . .

ENV PYTHONUNBUFFERED=1

CMD ["sleep", "infinity"]

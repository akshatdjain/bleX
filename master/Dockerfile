FROM python:3.13-slim

WORKDIR /app

# Install system dependencies if required for psycopg2 or others
RUN apt-get update && apt-get install -y libpq-dev gcc && rm -rf /var/lib/apt/lists/*

COPY pyproject.toml .

# Install dependencies directly using pip
RUN pip install --no-cache-dir \
    "paho-mqtt>=2.1.0" \
    "psycopg2>=2.9.11" \
    "redis>=7.3.0" \
    "requests>=2.32.5"

COPY . .

# Run unbuffered so logs print immediately via Docker logs
ENV PYTHONUNBUFFERED=1

# By default, do nothing. Docker Compose overrides the command.
CMD ["sleep", "infinity"]

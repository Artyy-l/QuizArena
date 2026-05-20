# Запуск QuizArena через Docker Compose

Проект можно поднять одной командой: Java-приложение, LLM-сервис, PostgreSQL, Redis, Kafka, Prometheus и Grafana запускаются в одной Docker-сети.

## Требования

- Docker и Docker Compose plugin.
- API-ключ OpenAI-compatible провайдера для LLM-сервиса.
- Свободный порт приложения, по умолчанию `8081`.

## Первый запуск

```bash
cp .env.example .env
# отредактируйте .env
docker compose up -d --build
```

В `.env` обязательно поменяйте:

- `POSTGRES_PASSWORD`
- `QUIZARENA_JWT_SECRET`, например через `openssl rand -base64 32`
- `OPENAI_API_KEY`
- `GF_SECURITY_ADMIN_PASSWORD`
- `QUIZARENA_CORS_ALLOWED_ORIGINS`, если приложение открывается через домен

После запуска:

```bash
docker compose ps
docker compose logs -f app
docker compose logs -f llm-service
```

Приложение будет доступно на `http://localhost:8081`, если не меняли `APP_BIND` и `APP_PORT`.

## Полезные команды

```bash
docker compose up -d --build
docker compose restart app
docker compose logs -f app
docker compose logs -f llm-service
docker compose down
```

Данные PostgreSQL, Kafka, Grafana, Prometheus, загруженные материалы и состояние LLM-сервиса хранятся в Docker volumes.

## Порты

По умолчанию наружу на `127.0.0.1` проброшены:

- приложение: `APP_PORT=8081`
- PostgreSQL: `POSTGRES_PORT=5433`
- Redis: `REDIS_PORT=6380`
- Kafka: `KAFKA_HOST_PORT=9092`
- LLM-сервис: `LLM_PORT=8000`
- Prometheus: `PROMETHEUS_PORT=9090`
- Grafana: `GRAFANA_PORT=3000`

Для доступа с другого компьютера поменяйте нужный `*_BIND` на `0.0.0.0`. Для публичного сервера обычно открывают только приложение, а остальные сервисы оставляют локальными.

## Проверка

```bash
curl http://localhost:8081/actuator/health
curl http://localhost:8000/health
```

Prometheus смотрит метрики Java-приложения и LLM-сервиса внутри docker-сети по адресам `app:8081` и `llm-service:8000`.

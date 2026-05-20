# QuizArena

QuizArena - веб-приложение для создания и прохождения квизов с генерацией вопросов при помощи ИИ. Пользователь может создать квиз по теме или учебному материалу, пройти его самостоятельно, запустить совместную сессию с друзьями и сравнить результаты в таблице лидеров.

Проект выполнен в рамках курсовой работы.

## Возможности

- регистрация и авторизация пользователей;
- создание, редактирование, копирование и удаление квизов;
- публичные и приватные квизы;
- генерация вопросов через отдельный LLM-сервис;
- поддержка разных типов вопросов: один ответ, несколько ответов, true/false, формат "100 к 1";
- загрузка материалов к квизу и извлечение текста из файлов;
- одиночное прохождение квизов с ограничением времени на вопрос;
- мультиплеерные сессии по ссылке: создание комнаты, подключение участников, запуск игры, live-прогресс и результаты;
- история прохождений, профиль пользователя и список созданных квизов;
- таблицы лидеров для квизов;
- метрики приложения и дашборды мониторинга через Prometheus и Grafana.

## Архитектура

Проект состоит из нескольких частей:

- `src/` - основное Java-приложение на Spring Boot;
- `LLM_service/` - Python/FastAPI-сервис для генерации вопросов через LLM;
- `monitoring/` - конфигурация Prometheus и Grafana;
- `docs/` - документы по курсовой работе;
- `docker-compose.yml` - инфраструктура для локального запуска.

Основной сценарий генерации выглядит так:

1. Пользователь создает квиз и указывает тему, количество вопросов и тип вопросов.
2. Spring Boot-приложение создает задачу генерации и отправляет ее в Kafka.
3. Kafka consumer обрабатывает задачу и вызывает FastAPI LLM-сервис.
4. LLM-сервис возвращает структурированный JSON с вопросами, вариантами ответов и пояснениями.
5. Spring Boot-приложение валидирует результат и сохраняет вопросы в PostgreSQL.
6. Пользователь проходит готовый квиз в одиночном или мультиплеерном режиме.

## Технологии

- Java 17;
- Spring Boot 3.2;
- Spring MVC, Spring Data JPA, Spring Security;
- Thymeleaf;
- PostgreSQL;
- Redis;
- Apache Kafka;
- Python, FastAPI;
- OpenAI-compatible LLM API;
- Apache Tika для извлечения текста из файлов;
- Prometheus, Grafana;
- Maven;
- Docker Compose.

## Требования

Для локального запуска нужны:

- JDK 17;
- Maven;
- Docker Desktop или Docker Engine с Docker Compose;
- Python 3.10+;
- ключ API для LLM-провайдера, совместимого с OpenAI API.

## Быстрый запуск

### 1. Запустить инфраструктуру

```bash
docker compose up -d
```

Команда поднимает:

- PostgreSQL на `localhost:5433`;
- Redis на `localhost:6380`;
- Kafka на `localhost:9092`;
- Prometheus на `http://localhost:9090`;
- Grafana на `http://localhost:3000`.

Данные для Grafana по умолчанию:

```text
login: admin
password: admin
```

### 2. Настроить LLM-сервис

```bash
cd LLM_service
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
copy .env.example .env
```

В файле `LLM_service/.env` нужно указать ключ и, при необходимости, модель:

```env
OPENAI_API_KEY=...
OPENAI_MODEL=gpt-4.1-mini
```

Если используется не OpenAI, а совместимый backend, дополнительно указывается:

```env
OPENAI_BASE_URL=https://example.com/v1
```

### 3. Запустить LLM-сервис

Из папки `LLM_service`:

```bash
uvicorn app.main:app --host 127.0.0.1 --port 8000 --reload
```

Проверка:

```bash
curl http://127.0.0.1:8000/health
```

### 4. Запустить Java-приложение

Из корня проекта:

```bash
mvn spring-boot:run
```

Приложение будет доступно по адресу:

```text
http://localhost:8081
```

## Конфигурация

Основные настройки Java-приложения находятся в `src/main/resources/application.properties`.

По умолчанию приложение использует:

- PostgreSQL: `jdbc:postgresql://localhost:5433/quizarena`;
- Redis: `localhost:6380`;
- Kafka: `localhost:9092`;
- LLM-сервис: `http://127.0.0.1:8000`;
- порт приложения: `8081`.

Для dev-режима есть профиль с H2:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

H2 Console будет доступна по адресу:

```text
http://localhost:8081/h2-console
```

## Основные страницы

- `/login` - вход;
- `/register` - регистрация;
- `/home` - список публичных квизов;
- `/quiz/create` - создание квиза;
- `/my-quizzes` - квизы текущего пользователя;
- `/history` - история прохождений;
- `/profile` - профиль пользователя;
- `/multiplayer/create` - создание мультиплеерной сессии;
- `/multiplayer/join` - подключение к сессии.

## API

Часть пользовательских сценариев реализована через REST API:

- `POST /api/auth/register` - регистрация;
- `POST /api/auth/login` - вход;
- `POST /api/quizzes` - создание квиза;
- `POST /api/quizzes/search` - поиск публичных квизов;
- `POST /api/quizzes/{quizId}/materials` - загрузка материалов;
- `POST /api/generation/generate` - запуск генерации вопросов;
- `POST /api/attempts/start` - старт попытки прохождения;
- `POST /api/attempts/answer` - отправка ответа;
- `POST /api/attempts/{attemptId}/finish` - завершение попытки;
- `POST /api/multiplayer/sessions` - создание мультиплеерной сессии;
- `POST /api/multiplayer/sessions/join` - подключение к сессии;
- `GET /api/multiplayer/sessions/{sessionId}/live-leaderboard` - текущая таблица лидеров сессии.

## LLM-сервис

FastAPI-сервис в `LLM_service/` отвечает за работу с языковой моделью:

- `GET /health` - проверка доступности;
- `GET /check-ethics/{prompt}` - проверка промпта;
- `POST /generate` - синхронная генерация;
- `POST /jobs/generate` - генерация через очередь задач внутри сервиса;
- `GET /jobs/{job_id}` - статус задачи;
- `GET /question/{prompt}/{number}` - legacy endpoint.

Сервис ожидает от модели структурированный JSON с вопросами, вариантами ответов, правильными ответами и пояснениями.

## Мониторинг

Spring Boot Actuator отдает метрики Prometheus:

```text
http://localhost:8081/actuator/prometheus
```

Prometheus доступен на:

```text
http://localhost:9090
```

Grafana доступна на:

```text
http://localhost:3000
```

В проекте уже есть provisioning для datasource и дашбордов в `monitoring/grafana/`.

## Документы

Документы по курсовой работе лежат в папке `docs/`.

Сейчас там находятся:

- техническое задание;
- пояснительная записка;
- дополнительные материалы по проекту.

## Структура проекта

```text
QuizArena/
  src/                 Java/Spring Boot приложение
  LLM_service/          FastAPI-сервис генерации вопросов
  monitoring/           Prometheus и Grafana
  docs/                 Документы курсовой работы
  docker-compose.yml    Локальная инфраструктура
  pom.xml               Maven-конфигурация
```

## Остановка

Остановить инфраструктуру:

```bash
docker compose down
```

Остановить инфраструктуру вместе с Docker volumes:

```bash
docker compose down -v
```

Второй вариант удалит локальные данные PostgreSQL, Prometheus и Grafana.

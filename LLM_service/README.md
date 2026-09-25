# Сервис генерации вопросов

FastAPI-сервис принимает тему и параметры квиза, обращается к OpenAI-совместимому API и возвращает структурированные вопросы. Основное приложение вызывает его через Kafka-обработчик; подробнее о запуске всего проекта — в [главном README](../README.md).

## Запуск отдельно

Требуются Python 3.11+ и API-ключ провайдера модели.

```bash
python -m venv .venv
python -m pip install -r requirements.txt
```

Активируйте виртуальное окружение, задайте `OPENAI_API_KEY` в окружении или в локальном `.env`, затем запустите:

```bash
uvicorn main:app --host 127.0.0.1 --port 8000
```

Шаблон переменных находится в `.env.example`. Основные настройки: `OPENAI_BASE_URL`, `OPENAI_MODEL`, `ETHICS_MODEL`, `MAX_PARALLEL_LLM_CALLS`, `WORKER_COUNT`, `REQUEST_TIMEOUT_SECONDS`, `ENABLE_REASONING` и `FORCE_JSON_RESPONSE`.

## API

| Маршрут | Назначение |
| --- | --- |
| `GET /health` | Проверка доступности сервиса |
| `GET /config` | Текущая конфигурация без API-ключа |
| `GET /check-ethics/{prompt}?has_material=true` | Проверка допустимости темы; при ошибке проверки возвращается `503` |
| `POST /jobs/generate` | Постановка задания в локальную очередь, ответ с `job_id` |
| `GET /jobs/{job_id}` | Статус и результат задания |
| `POST /generate` | Синхронная генерация |
| `GET /question/{prompt}/{number}` | Устаревший маршрут для совместимости |

`POST /jobs/generate` и `POST /generate` принимают form-data поля `topic` (тема или текст извлечённого материала), `number` (от 1 до 200), `question_types` (список через запятую) и `has_material` (`true`/`false`). Поддерживаются `single_choice`, `multiple_choice`, `true_false`, `100k1`. Поле `has_material` сообщает, что текст файла уже включён в `topic`, и меняет правила привязки вопросов к источнику.

Пример:

```bash
curl -X POST http://127.0.0.1:8000/jobs/generate \
  -F "topic=Линейная алгебра" \
  -F "number=6" \
  -F "question_types=single_choice,multiple_choice"
```

Сервис ожидает от модели JSON с вопросами, вариантами, `correct_answers`, пояснениями и, для «100 к 1», баллами `nominal`. Популярность в «100 к 1» — оценка модели, а не статистика реального опроса.

## Файлы и ограничения

Этот сервис сейчас **не принимает сами файлы**. Основное Java-приложение извлекает текст PDF, DOC/DOCX или TXT через Apache Tika и отправляет его в поле `topic`; изображения и схемы в этом потоке не передаются модели напрямую. Если JSON-ответ модели не распарсился, сырой ответ сохраняется в состоянии задания, а ошибка отражается в `errors`.

Задания хранятся в памяти процесса, а их состояния сохраняются в `storage/<job_id>/job_state.json`. После перезапуска локальная очередь и индекс заданий в памяти не восстанавливаются; старые файлы состояния остаются на диске.

## Тесты

Из каталога `LLM_service`:

```bash
python -m unittest discover -s tests -v
```

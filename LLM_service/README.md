## Возможности

- **Очередь задач** через `POST /jobs/generate`
- **Синхронная генерация** через `POST /generate`
- **Проверка статуса** через `GET /jobs/{job_id}`
- **Legacy-эндпоинт** `GET /question/{prompt}/{number}`
- **Healthcheck** `GET /health`
- **Просмотр конфигурации** `GET /config`
- **Поддержка PDF** через OpenAI Responses API (`input_file` с `data:application/pdf;base64,...`)
- **Ограничение параллелизма** запросов к LLM через semaphore
- **Сохранение состояния задач** в файловую систему

## Поддерживаемые типы вопросов

#### `single_choice`
Один правильный ответ. Для вопроса генерируется ровно 4 варианта.

#### `multiple_choice`
Несколько правильных ответов. Для вопроса генерируется от 4 до 7 вариантов, а в `correct_answers` должно быть минимум 2 правильных ответа.

#### `true_false`
Формат true/false. Варианты должны иметь идентификаторы `T` и `F`.

#### `100k1`
Формат по модели «100 к 1»:
- ровно 8 вариантов ответа;
- из них 5 правильных и 3 неправильных;
- в `correct_answers` перечисляются id всех 5 правильных вариантов.

## Установка

```bash
python -m venv .venv
source .venv/bin/activate  # Linux/macOS
# .venv\Scripts\activate   # Windows PowerShell

pip install -r requirements.txt
cp .env.example .env
```

### Описание переменных .env

- `OPENAI_API_KEY` - ключ доступа к LLM API
- `OPENAI_BASE_URL` - базовый URL OpenAI-compatible backend
- `LLM_MODEL` / `OPENAI_MODEL` - имя модели
- `ETHICS_MODEL` - модель для проверки этичности промпта
- `MAX_UPLOAD_SIZE_MB` - максимальный размер одного загружаемого файла
- `MAX_PARALLEL_LLM_CALLS` - сколько одновременных LLM-вызовов допускается внутри клиента
- `WORKER_COUNT` - число фоновых worker'ов для очереди задач
- `REQUEST_TIMEOUT_SECONDS` - timeout запросов к модели
- `ENABLE_REASONING` - включает передачу `reasoning.effort` в Responses API
- `REASONING_EFFORT` - уровень reasoning effort
- `FORCE_JSON_RESPONSE` - принудительный JSON-ответ от модели
- `STORAGE_DIR` - директория хранения задач и файлов
- `ALLOW_NON_PDF_FILES` - разрешить не-PDF файлы (по умолчанию `false`)

## Запуск

### Вариант 1

```bash
uvicorn main:app --host 127.0.0.1 --port 8000 --reload
```

### Вариант 2

```bash
uvicorn app.main:app --host 127.0.0.1 --port 8000 --reload
```

### Вариант 3

```bash
fastapi dev app/main.py
```

## API

### `GET /health`
Проверка, что сервис поднят.

Пример ответа:

```json
{
  "status": "ok",
  "app": "LLM Service"
}
```

### `GET /config`
Возвращает текущую конфигурацию сервиса без секрета API key.

Пример ответа:

```json
{
  "model_name": "gpt-4.1-mini",
  "api_base_url": "https://api.openai.com/v1",
  "storage_dir": "/absolute/path/to/storage",
  "max_upload_size_mb": 30,
  "max_parallel_llm_calls": 3,
  "worker_count": 3,
  "queue_size": 0,
  "jobs_total": 0
}
```

### `POST /jobs/generate`
Ставит задачу генерации в очередь и сразу возвращает `job_id`.

#### Form-data поля

- `topic` - тема
- `number` - количество вопросов от 1 до 200
- `question_types` - строка через запятую, например `single_choice,multiple_choice`
- `files` - один или несколько PDF-файлов

#### Пример запроса

```bash
curl -X POST http://127.0.0.1:8000/jobs/generate \
  -F "topic=Линейная алгебра" \
  -F "number=6" \
  -F "question_types=single_choice,multiple_choice,true_false" \
  -F "files=@./materials/lecture.pdf"
```

#### Пример ответа

```json
{
  "job_id": "2e7f3f3f-8f9e-4f33-8f5a-xxxxxxxxxxxx",
  "status": "queued",
  "created_at": "2026-03-30T10:00:00.000000"
}
```

### `GET /jobs/{job_id}`
Возвращает полное состояние задачи.

#### Пример ответа

```json
{
  "id": "2e7f3f3f-8f9e-4f33-8f5a-xxxxxxxxxxxx",
  "status": "finished",
  "created_at": "2026-03-30T10:00:00.000000",
  "started_at": "2026-03-30T10:00:01.000000",
  "finished_at": "2026-03-30T10:00:05.000000",
  "request": {
    "topic": "Линейная алгебра",
    "number": 6,
    "question_types": ["single_choice", "multiple_choice"]
  },
  "files": [
    "/absolute/path/to/storage/<job_id>/inputs/lecture.pdf"
  ],
  "errors": [],
  "logs": [
    "[10:00:00] Задание создано и поставлено в очередь",
    "[10:00:01] Задание взял worker #0",
    "[10:00:01] Начата обработка задания",
    "[10:00:05] Ответ успешно получен и распарсен"
  ],
  "result": {
    "raw_response": "{...}",
    "parsed_response": {
      "topic": "Линейная алгебра",
      "language": "ru",
      "question_count": 6,
      "questions": [],
      "warnings": []
    }
  }
}
```

### `POST /generate`
Синхронная генерация без постановки в очередь. Возвращает тот же формат `JobState`, но задача обрабатывается сразу.

#### Пример запроса

```bash
curl -X POST http://127.0.0.1:8000/generate \
  -F "topic=Теория вероятностей" \
  -F "number=4" \
  -F "question_types=true_false,single_choice"
```

### `GET /question/{prompt}/{number}`
Legacy-эндпоинт для обратной совместимости.

Пример:

```bash
curl "http://127.0.0.1:8000/question/Математический%20анализ/5"
```

## Как обрабатываются файлы

- По умолчанию принимаются только PDF-файлы
- Если `ALLOW_NON_PDF_FILES=false`, любой другой формат вызовет ошибку `400`
- Файл сохраняется в `storage/<job_id>/inputs/`
- Для LLM PDF кодируется в `data:application/pdf;base64,...`
- Если backend не поддерживает OpenAI Responses API с file inputs, запросы с PDF завершатся ошибкой

## Где хранятся результаты

Для каждой задачи создаётся директория:

```text
storage/<job_id>/
├── inputs/
│   └── uploaded.pdf
├── job_state.json
└── traceback.txt   # только если задача упала с ошибкой
```

## Формат результата модели

Сервис ожидает от модели JSON следующего вида:

```json
{
  "topic": "...",
  "language": "ru",
  "question_count": 4,
  "questions": [
    {
      "id": "q1",
      "type": "single_choice",
      "difficulty": "medium",
      "question": "...",
      "options": [
        {"id": "A", "text": "..."},
        {"id": "B", "text": "..."}
      ],
      "correct_answers": ["A"],
      "explanation": "...",
      "source_reference": "стр. 3",
      "metadata": {}
    }
  ],
  "warnings": []
}
```

Если JSON распарсить не удалось, сервис всё равно сохранит сырой ответ модели в `result.raw_response`, а в `errors` добавит сообщение об ошибке парсинга.

## Ограничения текущей реализации

1. Фактически сервис ориентирован на русский язык: prompt жёстко требует генерировать вопросы только на русском.
2. `topic`, `number`, `question_types` и `files` - основные реально используемые входные параметры.
3. Для PDF нужен backend, совместимый с OpenAI Responses API и `input_file`.
4. Очередь и состояния задач хранятся в памяти процесса и на локальном диске, без Redis/PostgreSQL.
5. После рестарта приложения in-memory очередь и список задач обнуляются.

## Что можно улучшить дальше

- добавить Redis/PostgreSQL для устойчивого хранения очереди;
- добавить TTL-очистку старых задач и файлов;
- сделать streaming-ответы через SSE/WebSocket;
- добавить retry/backoff для 429/5xx;
- вынести `language`, `difficulty`, `audience`, `extra_instructions` в реально используемый prompt и pydantic-схемы;
- добавить тесты на структуру JSON-ответа.

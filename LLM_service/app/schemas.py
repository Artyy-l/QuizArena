from __future__ import annotations

from datetime import datetime
from enum import Enum
from typing import Any
from uuid import uuid4

from pydantic import BaseModel, Field, field_validator


class JobStatus(str, Enum):
    queued = "queued"
    running = "running"
    finished = "finished"
    failed = "failed"


class QuestionType(str, Enum):
    single_choice = "single_choice"
    multiple_choice = "multiple_choice"
    true_false = "true_false"
    q100k1 = "100k1"


class GenerationRequest(BaseModel):
    topic: str = Field(..., min_length=3)
    number: int = Field(..., ge=1, le=200)
    question_types: list[QuestionType] = Field(
        default_factory=lambda: [QuestionType.single_choice]
    )

    @field_validator("topic")
    @classmethod
    def strip_topic(cls, value: str) -> str:
        stripped = value.strip()
        if not stripped:
            raise ValueError("topic must not be empty")
        return stripped

    @field_validator("question_types")
    @classmethod
    def ensure_question_types(cls, value: list[QuestionType]) -> list[QuestionType]:
        if not value:
            raise ValueError("question_types must not be empty")
        return value


class QuestionOption(BaseModel):
    id: str
    text: str


class QuestionItem(BaseModel):
    id: str
    type: QuestionType
    difficulty: str | None = None
    question: str
    options: list[QuestionOption] | None = None
    correct_answers: list[str] = Field(default_factory=list)
    explanation: str
    source_reference: str | None = None
    metadata: dict[str, Any] = Field(default_factory=dict)


class QuestionSet(BaseModel):
    topic: str
    language: str = "ru"
    question_count: int
    questions: list[QuestionItem]
    warnings: list[str] = Field(default_factory=list)


class JobCreateResponse(BaseModel):
    job_id: str
    status: JobStatus
    created_at: datetime


class JobResult(BaseModel):
    raw_response: str | None = None
    parsed_response: dict[str, Any] | None = None


class JobState(BaseModel):
    id: str = Field(default_factory=lambda: str(uuid4()))
    status: JobStatus = JobStatus.queued
    created_at: datetime = Field(default_factory=datetime.utcnow)
    started_at: datetime | None = None
    finished_at: datetime | None = None
    request: GenerationRequest
    files: list[str] = Field(default_factory=list)
    errors: list[str] = Field(default_factory=list)
    logs: list[str] = Field(default_factory=list)
    result: JobResult = Field(default_factory=JobResult)

    def add_log(self, message: str) -> None:
        now = datetime.utcnow().strftime("%H:%M:%S")
        self.logs.append(f"[{now}] {message}")
        self.logs = self.logs[-200:]

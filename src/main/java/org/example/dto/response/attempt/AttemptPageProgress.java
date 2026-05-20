package org.example.dto.response.attempt;

public record AttemptPageProgress(
        int totalQuestions,
        int questionsRemaining,
        int timePerQuestionSeconds,
        Long currentQuestionDeadlineEpochMs
) {}

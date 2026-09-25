package org.example.dto.request.quiz;

public record RemoveQuestionRequest(Long quizId, Long questionId, Long userId) {}

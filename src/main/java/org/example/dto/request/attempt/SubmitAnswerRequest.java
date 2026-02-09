package org.example.dto.request.attempt;

public record SubmitAnswerRequest(Long attemptId, Long questionId, Long selectedAnswerId) {}

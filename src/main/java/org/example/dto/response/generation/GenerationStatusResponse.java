package org.example.dto.response.generation;

public record GenerationStatusResponse(
        Long quizId,
        Long questionSetId,
        String status,
        String message,
        boolean finished,
        boolean failed
) {}

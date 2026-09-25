package org.example.dto.kafka;

import java.util.List;

public record QuizGenerationRequestMessage(
        String correlationId,
        Long questionSetId,
        Long quizId,
        String prompt,
        Integer questionCount,
        String preferredQuestionType,
        Stage stage,
        String mlJobId,
        List<String> materialFileUrls
) {}

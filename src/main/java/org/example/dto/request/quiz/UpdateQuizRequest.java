package org.example.dto.request.quiz;

import org.example.model.QuestionType;

public record UpdateQuizRequest(
        Long quizId,
        Long userId,
        String name,
        String prompt,
        Integer questionNumber,
        Integer timeLimit,
        Boolean isPrivate,
        Boolean isStatic,
        QuestionType defaultQuestionType
) {}

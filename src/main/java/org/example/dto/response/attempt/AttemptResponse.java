package org.example.dto.response.attempt;

import org.example.dto.response.quiz.QuestionDTO;

public record AttemptResponse(
  Long attemptId,
  Long quizId,
  String quizName,
  QuestionDTO currentQuestion,
  Integer questionsRemaining,
  Integer totalQuestions,
  Integer timeRemaining
) {}

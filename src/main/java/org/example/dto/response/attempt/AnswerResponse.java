package org.example.dto.response.attempt;

import org.example.dto.response.quiz.QuestionDTO;

public record AnswerResponse(
  Boolean isCorrect,
  String explanation,
  Long correctAnswerId,
  Integer scoreEarned,
  QuestionDTO nextQuestion,
  Long quizId
) {}

package org.example.dto.response.attempt;

import org.example.dto.response.quiz.QuestionDTO;

import java.util.List;

public record AnswerResponse(
  Boolean isCorrect,
  String explanation,
  Long correctAnswerId,
  List<Long> correctAnswerIds,
  Integer scoreEarned,
  QuestionDTO nextQuestion,
  Long quizId
) {}

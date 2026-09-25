package org.example.dto.response.attempt;

import org.example.dto.response.quiz.QuestionDTO;

import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

public record AnswerResponse(
  Boolean isCorrect,
  String explanation,
  Long correctAnswerId,
  List<Long> correctAnswerIds,
  Integer scoreEarned,
  QuestionDTO nextQuestion,
  Long quizId,
  Map<Long, BigDecimal> optionScores
) {}

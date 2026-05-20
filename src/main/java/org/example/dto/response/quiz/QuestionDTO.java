package org.example.dto.response.quiz;

import org.example.dto.common.AnswerOption;
import org.example.model.QuestionType;

import java.time.LocalDateTime;
import java.util.List;

public record QuestionDTO(
  Long id,
  String text,
  List<AnswerOption> options,
  QuestionType type,
  Integer timeLimit,
  String materialReference,
  String explanation,
  String difficulty,
  String category,
  Integer position,
  LocalDateTime createdAt,
  Boolean isCatInBagStakeScreen
) {
  public QuestionDTO(Long id, String text, List<AnswerOption> options, QuestionType type,
                     Integer timeLimit, String materialReference, String explanation,
                     String difficulty, String category, Integer position, LocalDateTime createdAt) {
    this(id, text, options, type, timeLimit, materialReference, explanation,
         difficulty, category, position, createdAt, null);
  }
}

package org.example.dto.response.attempt;

import java.time.LocalDateTime;

public record QuizResultDTO(
  Long attemptId,
  Integer score,
  Integer correctAnswers,
  Integer totalQuestions,
  Integer position,
  Long timeSpent,
  LocalDateTime completedAt,
  Integer catStake,
  Integer catStakeBonus,
  String mode,
  Integer accuracyPercent
) {
  /** Сохраняет совместимость со старым набором полей результата. */
  public QuizResultDTO(Long attemptId,
                       Integer score,
                       Integer correctAnswers,
                       Integer totalQuestions,
                       Integer position,
                       Long timeSpent,
                       LocalDateTime completedAt,
                       Integer catStake,
                       Integer catStakeBonus,
                       String mode) {
    this(attemptId, score, correctAnswers, totalQuestions, position, timeSpent,
            completedAt, catStake, catStakeBonus, mode, null);
  }
}

package org.example.dto.response.quiz;

import java.time.LocalDateTime;

public record QuizResponseDTO(
  Long quizId,
  String name,
  String status,
  LocalDateTime createdAt,
  String shareableId
) {}

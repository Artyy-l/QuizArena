package org.example.dto.response.quiz;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record QuizResponseDTO(
  @JsonProperty("id") Long quizId,
  String name,
  String status,
  LocalDateTime createdAt,
  String shareableId
) {}

package org.example.dto.response.quiz;

import org.example.dto.common.AnswerOption;

import java.time.LocalDateTime;
import java.util.List;

public record QuestionDTO(
  Long id,
  String text,
  List<AnswerOption> options,
  Integer timeLimit,
  String materialReference,
  String explanation,
  String difficulty,
  String category,
  Integer position,
  LocalDateTime createdAt
) {}

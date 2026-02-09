package org.example.dto.response.quiz;

import org.example.dto.common.QuizMaterial;

import java.time.LocalDateTime;
import java.util.List;

public record QuizDetailsDTO(
  Long id,
  String name,
  String description,
  String author,
  List<QuestionDTO> questions,
  List<QuizMaterial> materials,
  Integer questionNumber,
  Integer timeLimit,
  Integer timePerQuestion,
  Boolean isPublic,
  Boolean isStatic,
  String shareableId,
  LocalDateTime createdAt
) {}

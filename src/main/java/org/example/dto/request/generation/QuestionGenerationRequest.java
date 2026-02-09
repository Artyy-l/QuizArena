package org.example.dto.request.generation;

import org.example.dto.common.QuizMaterial;

import java.util.List;

public record QuestionGenerationRequest(
  Long quizId,
  String prompt,
  List<QuizMaterial> materials,
  Integer questionNumber,
  Integer questionCount
) {}

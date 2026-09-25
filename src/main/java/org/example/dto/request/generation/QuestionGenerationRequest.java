package org.example.dto.request.generation;

import org.example.dto.common.QuizMaterial;
import org.example.model.QuestionType;

import java.util.List;

public record QuestionGenerationRequest(
  Long quizId,
  String prompt,
  List<QuizMaterial> materials,
  Integer questionNumber,
  Integer questionCount,
  QuestionType preferredQuestionType
) {}

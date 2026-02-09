package org.example.dto.request.quiz;

import org.example.dto.common.QuizMaterial;

import java.util.List;

public record CreateQuizRequest(
  String name,
  String prompt,
  Long createdBy,
  Boolean hasMaterial,
  List<QuizMaterial> materials,
  Integer questionNumber,
  Integer timeLimit,
  Boolean isPrivate,
  Boolean isStatic
) {}

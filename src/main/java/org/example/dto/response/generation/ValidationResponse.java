package org.example.dto.response.generation;

import org.example.dto.common.ValidationError;

import java.util.List;

public record ValidationResponse(
  Long questionSetId,
  Integer totalQuestions,
  Integer validQuestions,
  List<ValidationError> errors
) {}

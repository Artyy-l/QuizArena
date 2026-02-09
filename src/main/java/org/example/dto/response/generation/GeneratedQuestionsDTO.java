package org.example.dto.response.generation;

import org.example.dto.common.GenerationMetadata;
import org.example.dto.response.quiz.QuestionDTO;

import java.util.List;

public record GeneratedQuestionsDTO(
  Long questionSetId,
  List<QuestionDTO> questions,
  GenerationMetadata metadata
) {}

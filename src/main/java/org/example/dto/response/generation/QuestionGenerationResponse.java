package org.example.dto.response.generation;

public record QuestionGenerationResponse(
  Long questionSetId,
  String status,
  Integer generatedCount,
  Integer validCount,
  Integer duplicateCount,
  Integer finalCount
) {}

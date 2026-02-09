package org.example.dto.request.quiz;

public record QuizSearchRequest(
  String query,
  String sortBy,
  Boolean ascending,
  Integer page,
  Integer size
) {}

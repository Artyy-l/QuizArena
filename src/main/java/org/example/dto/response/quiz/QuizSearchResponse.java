package org.example.dto.response.quiz;

import java.util.List;

public record QuizSearchResponse(
  List<QuizDTO> content,
  Integer currentPage,
  Integer totalPages,
  Long totalElements
) {}

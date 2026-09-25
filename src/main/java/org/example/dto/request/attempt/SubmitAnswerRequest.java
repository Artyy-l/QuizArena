package org.example.dto.request.attempt;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.List;

public record SubmitAnswerRequest(
  Long attemptId,
  Long questionId,
  @JsonAlias("answerId") Long selectedAnswerId,
  List<Long> selectedAnswerIds
) {
  public List<Long> getEffectiveSelectedIds() {
    if (selectedAnswerIds != null && !selectedAnswerIds.isEmpty()) {
      return selectedAnswerIds;
    }
    if (selectedAnswerId != null) {
      return List.of(selectedAnswerId);
    }
    return List.of();
  }
}

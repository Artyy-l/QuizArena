package org.example.dto.response.history;

import org.example.dto.common.AttemptSummary;
import org.example.dto.common.Statistics;

import java.util.List;

public record UserHistoryDTO(
  List<AttemptSummary> attempts,
  Statistics statistics
) {}

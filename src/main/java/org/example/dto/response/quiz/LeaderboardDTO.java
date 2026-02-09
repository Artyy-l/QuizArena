package org.example.dto.response.quiz;

import org.example.dto.common.LeaderboardEntry;

import java.util.List;

public record LeaderboardDTO(
  List<LeaderboardEntry> entries,
  Integer userPosition,
  Integer userScore
) {}

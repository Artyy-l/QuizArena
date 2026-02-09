package org.example.dto.response.multiplayer;

import org.example.dto.common.PlayerResult;

import java.time.LocalDateTime;
import java.util.List;

public record MultiplayerResultsDTO(
  String sessionId,
  List<PlayerResult> results,
  Integer userPosition,
  String quizName,
  LocalDateTime finishedAt
) {}

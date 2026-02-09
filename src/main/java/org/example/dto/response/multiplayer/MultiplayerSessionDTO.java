package org.example.dto.response.multiplayer;

import org.example.dto.common.ParticipantDTO;

import java.time.LocalDateTime;
import java.util.List;

public record MultiplayerSessionDTO(
  String sessionId,
  String quizName,
  Long hostUserId,
  String joinLink,
  List<ParticipantDTO> participants,
  String status,
  LocalDateTime createdAt
) {}

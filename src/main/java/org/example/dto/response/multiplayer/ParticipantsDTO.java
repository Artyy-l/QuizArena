package org.example.dto.response.multiplayer;

import org.example.dto.common.ParticipantDTO;

import java.util.List;

public record ParticipantsDTO(
  String sessionId,
  List<ParticipantDTO> participants,
  Integer totalCount
) {}

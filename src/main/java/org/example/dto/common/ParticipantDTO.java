package org.example.dto.common;

import java.time.LocalDateTime;

public record ParticipantDTO(Long userId, String username, LocalDateTime joinedAt) {}

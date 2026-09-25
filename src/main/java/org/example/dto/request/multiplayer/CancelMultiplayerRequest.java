package org.example.dto.request.multiplayer;

public record CancelMultiplayerRequest(String sessionId, Long hostUserId) {}

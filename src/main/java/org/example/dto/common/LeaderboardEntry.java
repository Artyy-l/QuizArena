package org.example.dto.common;

public record LeaderboardEntry(Integer position, String username, Integer score,
                               Long timeSpent) {}

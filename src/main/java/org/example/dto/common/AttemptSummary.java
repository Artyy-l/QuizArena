package org.example.dto.common;

import java.time.LocalDateTime;

public record AttemptSummary(Long attemptId, String quizName, Integer score,
                             LocalDateTime completedAt) {}

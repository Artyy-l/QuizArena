package org.example.dto.response.auth;

import java.time.LocalDateTime;

public record UserProfileDTO(Long id, String username, LocalDateTime registrationDate,
                             Integer totalQuizzes, Integer totalAttempts) {}

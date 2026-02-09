package org.example.dto.request.attempt;

public record StartAttemptRequest(Long userId, Long quizId, String sessionId) {
    public StartAttemptRequest(Long userId, Long quizId) {
        this(userId, quizId, null);
    }
}

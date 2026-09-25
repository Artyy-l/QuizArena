package org.example.dto.request.attempt;

public record StartAttemptRequest(Long userId, Long quizId, String sessionId, Integer catQuestionIndex) {
    public StartAttemptRequest(Long userId, Long quizId) {
        this(userId, quizId, null, null);
    }

    public StartAttemptRequest(Long userId, Long quizId, String sessionId) {
        this(userId, quizId, sessionId, null);
    }
}

package org.example.service;

import org.example.model.UserQuizAttempt;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttemptServiceDeadlineTest {

    @Test
    void acceptsAnswerSentAtDeadlineWithTransportDelay() {
        Instant deadline = Instant.parse("2026-01-01T12:00:00Z");
        UserQuizAttempt attempt = new UserQuizAttempt();
        attempt.setCurrentQuestionId(42L);
        attempt.setCurrentQuestionDeadlineAt(deadline);

        assertFalse(AttemptService.isTimedOut(attempt, 42L, deadline));
        assertFalse(AttemptService.isTimedOut(attempt, 42L, deadline.plusSeconds(5)));
        assertTrue(AttemptService.isTimedOut(attempt, 42L, deadline.plusSeconds(6)));
        assertFalse(AttemptService.isTimedOut(attempt, 43L, deadline.plusSeconds(6)));
    }
}

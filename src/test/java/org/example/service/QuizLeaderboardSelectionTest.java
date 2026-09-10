package org.example.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dto.response.quiz.LeaderboardDTO;
import org.example.model.Quiz;
import org.example.model.User;
import org.example.model.UserQuizAttempt;
import org.example.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuizLeaderboardSelectionTest {
    @Mock QuizRepository quizRepository;
    @Mock QuestionRepository questionRepository;
    @Mock AnswerOptionRepository answerOptionRepository;
    @Mock UserQuizAttemptRepository attemptRepository;
    @Mock UserAnswerRepository userAnswerRepository;
    @Mock GenerationSetRepository generationSetRepository;
    @Mock MultiplayerSessionRepository multiplayerSessionRepository;
    @Mock UserRepository userRepository;
    @Mock FileStorageService fileStorageService;
    @Mock QuestionGenerationService questionGenerationService;
    @Mock FastApiClient fastApiClient;
    @Mock LeaderboardService leaderboardService;
    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock ObjectMapper objectMapper;
    @Mock PlatformTransactionManager transactionManager;
    @InjectMocks QuizService service;

    @Test
    void selectsOneBestSoloAttemptByScoreAccuracyAttemptNumberThenTime() {
        User user = new User(1L, "arty", "hash");
        User otherUser = new User(2L, "other", "hash");
        Quiz quiz = new Quiz();
        quiz.setId(10L);
        quiz.setPrivate(false);

        UserQuizAttempt first = attempt(1L, user, quiz, 3L, 30, 120);
        UserQuizAttempt second = attempt(2L, user, quiz, 3L, 50, 90);
        UserQuizAttempt third = attempt(3L, user, quiz, 3L, 50, 60);
        UserQuizAttempt fourth = attempt(4L, otherUser, quiz, 4L, 0, 30);

        when(quizRepository.findById(10L)).thenReturn(Optional.of(quiz));
        when(attemptRepository.findCompletedByQuizIdOrderByScoreDesc(eq(10L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second, third, fourth)));
        for (long id = 2; id <= 3; id++) {
            when(attemptRepository.countCompletedByUserIdAndQuizIdUpToAttemptId(1L, 10L, id))
                    .thenReturn(id);
        }
        when(attemptRepository.countCompletedByUserIdAndQuizIdUpToAttemptId(2L, 10L, 4L))
                .thenReturn(1L);

        LeaderboardDTO leaderboard = service.getQuizLeaderboard(10L, 1L, "solo");

        assertEquals(2, leaderboard.entries().size());
        assertEquals(4, leaderboard.entries().get(0).score());
        assertEquals(0, leaderboard.entries().get(0).accuracyPercent());
        assertEquals(3, leaderboard.entries().get(1).score());
        assertEquals(50, leaderboard.entries().get(1).accuracyPercent());
        assertEquals(2, leaderboard.entries().get(1).attemptNumber());
        assertEquals(90L, leaderboard.entries().get(1).timeSpent());
        verify(attemptRepository).findCompletedByQuizIdOrderByScoreDesc(eq(10L), any(Pageable.class));
    }

    @Test
    void commonLeaderboardIncludesBestMultiplayerAttemptWithoutStake() {
        User alice = new User(1L, "alice", "hash");
        User bob = new User(2L, "bob", "hash");
        Quiz quiz = new Quiz();
        quiz.setId(10L);
        quiz.setPrivate(false);

        UserQuizAttempt aliceSolo = attempt(1L, alice, quiz, 42L, 80, 90);
        UserQuizAttempt aliceMultiplayer = attempt(2L, alice, quiz, 142L, 90, 100);
        aliceMultiplayer.setSessionId("session-a");
        aliceMultiplayer.setBaseScore(40L);
        UserQuizAttempt bobMultiplayer = attempt(3L, bob, quiz, 60L, 70, 110);
        bobMultiplayer.setSessionId("session-a");
        bobMultiplayer.setBaseScore(50L);

        when(quizRepository.findById(10L)).thenReturn(Optional.of(quiz));
        when(attemptRepository.findCompletedByQuizIdOrderByScoreDesc(eq(10L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(aliceMultiplayer, bobMultiplayer, aliceSolo)));

        LeaderboardDTO leaderboard = service.getQuizLeaderboard(10L, 1L, "solo");

        assertEquals(2, leaderboard.entries().size());
        assertEquals("bob", leaderboard.entries().get(0).username());
        assertEquals(50, leaderboard.entries().get(0).score());
        assertEquals("alice", leaderboard.entries().get(1).username());
        assertEquals(42, leaderboard.entries().get(1).score());
        assertEquals(2, leaderboard.userPosition());
        assertEquals(42, leaderboard.userScore());
        verify(attemptRepository).findCompletedByQuizIdOrderByScoreDesc(eq(10L), any(Pageable.class));
    }

    private UserQuizAttempt attempt(Long id, User user, Quiz quiz, Long score,
                                    int accuracy, long durationSeconds) {
        UserQuizAttempt attempt = new UserQuizAttempt();
        attempt.setId(id);
        attempt.setUser(user);
        attempt.setQuiz(quiz);
        attempt.setScore(score);
        attempt.setBaseScore(score);
        attempt.setAccuracyPercent(accuracy);
        attempt.setStartTime(Instant.parse("2026-01-01T12:00:00Z"));
        attempt.setFinishTime(attempt.getStartTime().plusSeconds(durationSeconds));
        attempt.setCompleted(true);
        return attempt;
    }
}

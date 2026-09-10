package org.example.security;

import jakarta.servlet.http.HttpServletRequest;
import org.example.model.GenerationSet;
import org.example.model.UserQuizAttempt;
import org.example.repository.GenerationSetRepository;
import org.example.repository.QuizRepository;
import org.example.repository.UserQuizAttemptRepository;
import org.example.service.JwtService;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** Определяет пользователя по подписанному токену, а не по переданному клиентом ID. */
@Component
public class RequestAuthorization {
    private final JwtService jwtService;
    private final QuizRepository quizRepository;
    private final UserQuizAttemptRepository attemptRepository;
    private final GenerationSetRepository generationSetRepository;

    public RequestAuthorization(JwtService jwtService, QuizRepository quizRepository,
                                UserQuizAttemptRepository attemptRepository,
                                GenerationSetRepository generationSetRepository) {
        this.jwtService = jwtService;
        this.quizRepository = quizRepository;
        this.attemptRepository = attemptRepository;
        this.generationSetRepository = generationSetRepository;
    }

    public Long requireUserId(HttpServletRequest request) {
        Long userId = jwtService.extractUserIdFromRequest(request);
        if (userId == null) {
            throw new SecurityException("Требуется авторизация");
        }
        return userId;
    }

    public Long requireSameUser(HttpServletRequest request, Long claimedUserId) {
        Long userId = requireUserId(request);
        if (!Objects.equals(userId, claimedUserId)) {
            throw new SecurityException("Нельзя действовать от имени другого пользователя");
        }
        return userId;
    }

    public void requireQuizOwner(HttpServletRequest request, Long quizId) {
        Long userId = requireUserId(request);
        if (!quizRepository.isCreator(quizId, userId)) {
            throw new SecurityException("Только автор может изменять квиз");
        }
    }

    public void requireAttemptOwner(HttpServletRequest request, Long attemptId) {
        Long userId = requireUserId(request);
        UserQuizAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("Попытка не найдена"));
        if (!userId.equals(attempt.getUser().getId())) {
            throw new SecurityException("Доступ к чужой попытке запрещён");
        }
    }

    public void requireGenerationSetOwner(HttpServletRequest request, Long generationSetId) {
        Long userId = requireUserId(request);
        GenerationSet generationSet = generationSetRepository.findById(generationSetId)
                .orElseThrow(() -> new IllegalArgumentException("Набор вопросов не найден"));
        if (!userId.equals(generationSet.getQuiz().getCreatedBy().getId())) {
            throw new SecurityException("Доступ к чужой генерации запрещён");
        }
    }
}

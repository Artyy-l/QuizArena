package org.example.service;

import org.example.dto.request.multiplayer.*;
import org.example.dto.response.multiplayer.MultiplayerResultsDTO;
import org.example.dto.response.multiplayer.MultiplayerSessionDTO;
import org.example.dto.response.multiplayer.ParticipantsDTO;
import org.example.dto.common.ParticipantDTO;
import org.example.dto.common.PlayerResult;
import org.example.model.*;
import org.example.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import jakarta.persistence.EntityManager;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Transactional
public class MultiplayerService {
    private final AttemptService attemptService;
    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();
    private final MultiplayerSessionRepository sessionRepository;
    @Autowired
    private EntityManager entityManager;
    private final QuizRepository quizRepository;
    private final UserRepository userRepository;
    private final UserQuizAttemptRepository attemptRepository;
    private final UserAnswerRepository userAnswerRepository;
    private final AttemptQuestionRepository attemptQuestionRepository;

    @Autowired
    public MultiplayerService(
            AttemptService attemptService,
            MultiplayerSessionRepository sessionRepository,
            QuizRepository quizRepository,
            UserRepository userRepository,
            UserQuizAttemptRepository attemptRepository,
            UserAnswerRepository userAnswerRepository,
            AttemptQuestionRepository attemptQuestionRepository) {
        this.attemptService = attemptService;
        this.sessionRepository = sessionRepository;
        this.quizRepository = quizRepository;
        this.userRepository = userRepository;
        this.attemptRepository = attemptRepository;
        this.userAnswerRepository = userAnswerRepository;
        this.attemptQuestionRepository = attemptQuestionRepository;
    }

    private static class SessionState {
        String sessionId;
        Long quizId;
        List<Long> participantIds;
        Instant startTime;
    }

    public MultiplayerSessionDTO createMultiplayerSession(CreateMultiplayerRequest request) {
        User host = userRepository.findById(request.userId())
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        Quiz quiz = quizRepository.findById(request.quizId())
                .orElseThrow(() -> new IllegalArgumentException("Квиз не найден"));

        String sessionId = UUID.randomUUID().toString();

        MultiplayerSession session = new MultiplayerSession();
        session.setSessionId(sessionId);
        session.setQuiz(quiz);
        session.setHostUser(host);
        session.setStatus("WAITING");
        session.setCreatedAt(Instant.now());
        session = sessionRepository.save(session);

        UserQuizAttempt hostAttempt = new UserQuizAttempt();
        hostAttempt.setUser(host);
        hostAttempt.setQuiz(quiz);
        hostAttempt.setStartTime(null);
        hostAttempt.setCompleted(false);
        hostAttempt.setScore(null);
        hostAttempt.setSessionId(sessionId);
        attemptRepository.save(hostAttempt);

        String joinLink = "/multiplayer/join?sessionId=" + sessionId;

        List<ParticipantDTO> participants = new ArrayList<>();
        participants.add(new ParticipantDTO(
                host.getId(),
                host.getLogin(),
                toLocalDateTime(session.getCreatedAt())
        ));

        return new MultiplayerSessionDTO(
                sessionId,
                quiz.getName(),
                host.getId(),
                joinLink,
                participants,
                session.getStatus(),
                toLocalDateTime(session.getCreatedAt())
        );
    }

    public MultiplayerSessionDTO getMultiplayerSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            throw new IllegalArgumentException("SessionId не может быть пустым");
        }
        MultiplayerSession session = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> {
                    return new IllegalArgumentException("Сессия не найдена: " + sessionId);
                });

        String quizName = session.getQuiz().getName();
        Long hostUserId = session.getHostUser().getId();
        String sessionStatus = session.getStatus();
        Instant createdAt = session.getCreatedAt();
        
        entityManager.clear();
        List<UserQuizAttempt> sessionAttempts = attemptRepository.findBySessionIdWithUser(sessionId);

        List<ParticipantDTO> participantDTOs = sessionAttempts.stream()
                .filter(a -> a.getUser() != null)
                .map(attempt -> new ParticipantDTO(
                        attempt.getUser().getId(),
                        attempt.getUser().getLogin(),
                        toLocalDateTime(attempt.getStartTime() != null 
                                ? attempt.getStartTime() 
                                : createdAt)
                ))
                .collect(Collectors.toList());
        

        String joinLink = "/multiplayer/join?sessionId=" + sessionId;

        return new MultiplayerSessionDTO(
                sessionId,
                quizName,
                hostUserId,
                joinLink,
                participantDTOs,
                sessionStatus,
                toLocalDateTime(createdAt)
        );
    }

    public boolean joinMultiplayerSession(JoinMultiplayerRequest request) {
        MultiplayerSession session = sessionRepository.findBySessionId(request.sessionId())
                .orElseThrow(() -> new IllegalArgumentException("Сессия не найдена"));

        if (!"WAITING".equals(session.getStatus())) {
            throw new IllegalStateException("Сессия уже начата или отменена");
        }

        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        UserQuizAttempt existingAttempt = attemptRepository.findByUserIdAndQuizIdAndSessionId(
                user.getId(), session.getQuiz().getId(), session.getSessionId());

        if (existingAttempt != null) {
            return true;
        }
        

        UserQuizAttempt participantAttempt = new UserQuizAttempt();
        participantAttempt.setUser(user);
        participantAttempt.setQuiz(session.getQuiz());
        participantAttempt.setStartTime(null);
        participantAttempt.setCompleted(false);
        participantAttempt.setScore(null);
        participantAttempt.setSessionId(session.getSessionId());
        attemptRepository.save(participantAttempt);

        return true;
    }

    public ParticipantsDTO getSessionParticipants(String sessionId) {
        MultiplayerSession session = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Сессия не найдена"));

        Instant createdAt = session.getCreatedAt();
        entityManager.clear();
        List<UserQuizAttempt> sessionAttempts = attemptRepository.findBySessionIdWithUser(sessionId);

        List<ParticipantDTO> participantDTOs = sessionAttempts.stream()
                .filter(a -> a.getUser() != null)
                .map(attempt -> new ParticipantDTO(
                        attempt.getUser().getId(),
                        attempt.getUser().getLogin(),
                        toLocalDateTime(attempt.getStartTime() != null 
                                ? attempt.getStartTime() 
                                : createdAt)
                ))
                .collect(Collectors.toList());
        

        return new ParticipantsDTO(
                sessionId,
                participantDTOs,
                participantDTOs.size()
        );
    }

    public boolean startMultiplayerSession(StartMultiplayerRequest request) {
        MultiplayerSession session = sessionRepository.findBySessionId(request.sessionId())
                .orElseThrow(() -> new IllegalArgumentException("Сессия не найдена"));

        if (!session.getHostUser().getId().equals(request.hostUserId())) {
            throw new SecurityException("Только хост может запустить сессию");
        }

        if (!"WAITING".equals(session.getStatus())) {
            throw new IllegalStateException("Сессия уже начата или отменена");
        }

        String sessionIdStr = request.sessionId();
        List<UserQuizAttempt> attempts = attemptRepository.findByQuizId(session.getQuiz().getId());
        List<UserQuizAttempt> sessionAttempts = attempts.stream()
                .filter(a -> sessionIdStr != null && sessionIdStr.equals(a.getSessionId()))
                .collect(Collectors.toList());

        if (sessionAttempts.size() < 2) {
            throw new IllegalStateException("Недостаточно участников для начала");
        }

        session.setStatus("STARTED");
        session.setStartedAt(Instant.now());
        sessionRepository.save(session);

        Instant now = Instant.now();
        for (UserQuizAttempt attempt : sessionAttempts) {
            attempt.setStartTime(now);
            attemptRepository.save(attempt);
        }

        return true;
    }

    public MultiplayerResultsDTO getMultiplayerResults(String sessionId) {
        MultiplayerSession session = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Сессия не найдена"));

        if (session.getQuiz() == null) {
            throw new IllegalStateException("Сессия не содержит квиз");
        }

        List<UserQuizAttempt> attempts = attemptRepository.findBySessionIdWithUser(sessionId);
        if (attempts == null || attempts.isEmpty()) {
            return new MultiplayerResultsDTO(
                    sessionId,
                    new ArrayList<>(),
                    null,
                    session.getQuiz().getName(),
                    null
            );
        }
        
        List<UserQuizAttempt> completedAttempts = attempts.stream()
                .filter(a -> a != null && a.isCompleted())
                .collect(Collectors.toList());

        if (completedAttempts.size() >= 2 && !"FINISHED".equals(session.getStatus())) {
            session.setStatus("FINISHED");
            session.setFinishedAt(Instant.now());
            sessionRepository.save(session);
        }

        List<UserQuizAttempt> sessionAttempts = completedAttempts;

        List<PlayerResult> results = new ArrayList<>();
        for (UserQuizAttempt attempt : sessionAttempts) {
            if (attempt.getUser() == null || attempt.getUser().getLogin() == null) {
                continue;
            }
            
            int score = attempt.getScore() != null ? attempt.getScore().intValue() : 0;
            long timeSpent = 0;
            if (attempt.getStartTime() != null && attempt.getFinishTime() != null) {
                timeSpent = java.time.Duration.between(
                        attempt.getStartTime(),
                        attempt.getFinishTime()
                ).getSeconds();
            }

            results.add(new PlayerResult(
                    0,
                    attempt.getUser().getLogin(),
                    score,
                    timeSpent
            ));
        }

        results.sort((a, b) -> {
            int scoreCompare = Integer.compare(b.score(), a.score());
            if (scoreCompare != 0) return scoreCompare;
            return Long.compare(a.timeSpent(), b.timeSpent());
        });

        List<PlayerResult> finalResults = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            finalResults.add(new PlayerResult(
                    i + 1,
                    results.get(i).username(),
                    results.get(i).score(),
                    results.get(i).timeSpent()
            ));
        }

        if (session.getQuiz() == null) {
            throw new IllegalStateException("Сессия не содержит квиз");
        }
        
        return new MultiplayerResultsDTO(
                sessionId,
                finalResults,
                null,
                session.getQuiz().getName(),
                toLocalDateTime(session.getFinishedAt())
        );
    }

    public boolean cancelMultiplayerSession(CancelMultiplayerRequest request) {
        MultiplayerSession session = sessionRepository.findBySessionId(request.sessionId())
                .orElseThrow(() -> new IllegalArgumentException("Сессия не найдена"));

        if (!session.getHostUser().getId().equals(request.hostUserId())) {
            throw new SecurityException("Только хост может отменить сессию");
        }

        if ("FINISHED".equals(session.getStatus())) {
            throw new IllegalStateException("Сессия уже завершена");
        }

        session.setStatus("CANCELLED");
        sessionRepository.save(session);

        if (session.getStartedAt() == null) {
            userAnswerRepository.deleteBySessionId(session.getSessionId());
            attemptRepository.deleteBySessionId(session.getSessionId());
        }

        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean leaveMultiplayerSession(String sessionId, Long userId) {
        MultiplayerSession session = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Сессия не найдена"));

        if (!"WAITING".equals(session.getStatus())) {
            throw new IllegalStateException("Нельзя покинуть сессию, которая уже начата");
        }

        Long quizId = session.getQuiz().getId();
        
        userAnswerRepository.deleteByUserIdAndQuizIdAndSessionId(userId, quizId, sessionId);
        
        int deleted = attemptRepository.deleteByUserIdAndQuizIdAndSessionId(userId, quizId, sessionId);
        
        return deleted > 0;
    }

    public Map<String, Object> getSessionProgress(String sessionId, Long questionId, Long currentUserId) {
        MultiplayerSession session = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Сессия не найдена"));

        List<UserQuizAttempt> attempts = attemptRepository.findBySessionId(sessionId);
        if (attempts.size() < 2) {
            return Map.of(
                    "bothAnswered", false,
                    "currentQuestionAnswered", false,
                    "currentQuestionId", questionId != null ? questionId : 0L
            );
        }

        UserQuizAttempt currentPlayerAttempt = null;
        UserQuizAttempt opponentAttempt = null;
        
        for (UserQuizAttempt attempt : attempts) {
            if (attempt.getUser() != null && attempt.getUser().getId().equals(currentUserId)) {
                currentPlayerAttempt = attempt;
            } else {
                opponentAttempt = attempt;
            }
        }

        if (currentPlayerAttempt == null || opponentAttempt == null) {
            return Map.of(
                    "bothAnswered", false,
                    "currentQuestionAnswered", false,
                    "currentQuestionId", questionId != null ? questionId : 0L
            );
        }

        boolean bothAnswered = false;
        boolean currentQuestionAnswered = false;

        if (questionId != null) {
            boolean currentPlayerAnswered = userAnswerRepository.existsByAttemptIdAndQuestionId(
                    currentPlayerAttempt.getId(), questionId);
            
            boolean opponentAnsweredOnCurrent = userAnswerRepository.existsByAttemptIdAndQuestionId(
                    opponentAttempt.getId(), questionId);
            
            bothAnswered = currentPlayerAnswered && opponentAnsweredOnCurrent;
            
            long currentPlayerAnswersCount = userAnswerRepository.countByAttemptId(currentPlayerAttempt.getId());
            long opponentAnswersCount = userAnswerRepository.countByAttemptId(opponentAttempt.getId());
            
            currentQuestionAnswered = opponentAnsweredOnCurrent || (opponentAnswersCount > currentPlayerAnswersCount);
        }

        return Map.of(
                "bothAnswered", bothAnswered,
                "currentQuestionAnswered", currentQuestionAnswered,
                "currentQuestionId", questionId != null ? questionId : 0L
        );
    }

    public Map<String, Object> getSessionLiveLeaderboard(String sessionId) {
        MultiplayerSession session = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Сессия не найдена"));

        List<UserQuizAttempt> attempts = attemptRepository.findBySessionIdWithUser(sessionId);
        List<Map<String, Object>> players = attempts.stream()
                .filter(a -> a.getUser() != null)
                .map(a -> {
                    long score = Math.round(attemptService.getCurrentScore(a.getId()));
                    long timeSpent = 0L;
                    if (a.getStartTime() != null) {
                        Instant end = a.getFinishTime() != null ? a.getFinishTime() : Instant.now();
                        timeSpent = Math.max(0L, java.time.Duration.between(a.getStartTime(), end).getSeconds());
                    }
                    return Map.<String, Object>of(
                            "userId", a.getUser().getId(),
                            "username", a.getUser().getLogin() != null ? a.getUser().getLogin() : "Unknown",
                            "score", score,
                            "completed", a.isCompleted(),
                            "timeSpent", timeSpent
                    );
                })
                .sorted((p1, p2) -> {
                    int scoreCmp = Long.compare((Long) p2.get("score"), (Long) p1.get("score"));
                    if (scoreCmp != 0) {
                        return scoreCmp;
                    }
                    return Long.compare((Long) p1.get("timeSpent"), (Long) p2.get("timeSpent"));
                })
                .collect(Collectors.toCollection(ArrayList::new));

        for (int i = 0; i < players.size(); i++) {
            players.set(i, Map.of(
                    "position", i + 1,
                    "userId", players.get(i).get("userId"),
                    "username", players.get(i).get("username"),
                    "score", players.get(i).get("score"),
                    "completed", players.get(i).get("completed"),
                    "timeSpent", players.get(i).get("timeSpent")
            ));
        }

        return Map.of(
                "sessionId", sessionId,
                "quizId", session.getQuiz() != null ? session.getQuiz().getId() : null,
                "status", session.getStatus(),
                "players", players
        );
    }


    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant != null
                ? LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
                : null;
    }
}

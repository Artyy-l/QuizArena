package org.example.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dto.request.generation.QuestionGenerationRequest;
import org.example.dto.request.quiz.*;
import org.example.dto.response.generation.GenerationStatusResponse;
import org.example.dto.response.quiz.*;
import org.example.dto.common.LeaderboardEntry;
import org.example.dto.common.QuizMaterial;
import org.example.model.GenerationSet;
import org.example.model.QuestionType;
import org.example.model.Quiz;
import org.example.model.User;
import org.example.mapper.QuizMapper;
import org.example.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
public class QuizService {
    private static final String QUIZ_CACHE_KEY = "quiz:v2:%d";
    private static final String STATUS_READY = "READY";
    private static final String STATUS_FAILED = "FAILED";
    private static final Duration QUIZ_CACHE_TTL = Duration.ofMinutes(30);
    private static final int GENERATED_QUESTION_POOL_SIZE = 30;

    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final AnswerOptionRepository answerOptionRepository;
    private final UserQuizAttemptRepository attemptRepository;
    private final UserAnswerRepository userAnswerRepository;
    private final GenerationSetRepository generationSetRepository;
    private final MultiplayerSessionRepository multiplayerSessionRepository;
    private final org.example.repository.UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final QuestionGenerationService questionGenerationService;
    private final FastApiClient fastApiClient;
    private final LeaderboardService leaderboardService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate requiresNewTx;

    @Autowired
    public QuizService(QuizRepository quizRepository,
                      QuestionRepository questionRepository,
                      AnswerOptionRepository answerOptionRepository,
                      UserQuizAttemptRepository attemptRepository,
                      UserAnswerRepository userAnswerRepository,
                      GenerationSetRepository generationSetRepository,
                      MultiplayerSessionRepository multiplayerSessionRepository,
                      org.example.repository.UserRepository userRepository,
                      FileStorageService fileStorageService,
                      QuestionGenerationService questionGenerationService,
                      FastApiClient fastApiClient,
                      LeaderboardService leaderboardService,
                      RedisTemplate<String, String> redisTemplate,
                      ObjectMapper objectMapper,
                      PlatformTransactionManager transactionManager) {
        this.quizRepository = quizRepository;
        this.questionRepository = questionRepository;
        this.answerOptionRepository = answerOptionRepository;
        this.attemptRepository = attemptRepository;
        this.userAnswerRepository = userAnswerRepository;
        this.generationSetRepository = generationSetRepository;
        this.multiplayerSessionRepository = multiplayerSessionRepository;
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
        this.questionGenerationService = questionGenerationService;
        this.fastApiClient = fastApiClient;
        this.leaderboardService = leaderboardService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requiresNewTx = transactionTemplate;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public QuizResponseDTO createQuiz(CreateQuizRequest request) {
        User creator = userRepository.findById(request.createdBy())
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: " + request.createdBy()));

        boolean hasFile = Boolean.TRUE.equals(request.hasMaterial());
        if ((request.prompt() == null || request.prompt().trim().isEmpty()) && !hasFile) {
            throw new IllegalArgumentException("Укажите тему квиза или добавьте материал");
        }
        try {
            // Проверяем безопасность запроса до сохранения, чтобы после отказа
            // проверки не оставался пустой квиз.
            questionGenerationService.checkPromptSafety(request.prompt(), hasFile);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GenerationRetryableException("Проверка безопасности была прервана", e);
        } catch (IOException e) {
            throw new GenerationRetryableException("Проверка безопасности временно недоступна", e);
        }

        record CreatedQuiz(Quiz quiz, boolean hasFile) {}

        CreatedQuiz createdQuiz = requiresNewTx.execute(status -> {
            Quiz quiz = new Quiz();
            quiz.setName(request.name());
            quiz.setPrompt(request.prompt());
            quiz.setCreatedBy(creator);
            quiz.setHasMaterial(request.hasMaterial() != null && request.hasMaterial());
            quiz.setMaterialUrl(null);
            quiz.setQuestionNumber(request.questionNumber());
            quiz.setTimePerQuestion(request.timeLimit() != null ?
                    java.time.Duration.ofSeconds(request.timeLimit()) : null);
            quiz.setPrivate(request.isPrivate() != null && request.isPrivate());
            quiz.setStatic(request.isStatic() != null && request.isStatic());
            quiz.setCreatedAt(Instant.now());
            quiz.setDefaultQuestionType(resolveDefaultQuestionType(request.defaultQuestionType()));

            quiz = quizRepository.save(quiz);

            return new CreatedQuiz(quiz, hasFile);
        });

        if (createdQuiz == null || createdQuiz.quiz() == null) {
            throw new RuntimeException("Не удалось создать квиз");
        }

        Quiz quiz = createdQuiz.quiz();

        if (request.prompt() != null && !request.prompt().trim().isEmpty() && !hasFile) {
            try {
                int questionCountForGeneration = GENERATED_QUESTION_POOL_SIZE;
                
                requiresNewTx.execute(status -> {
                    long existingQuestionCount = questionRepository.countByQuizId(quiz.getId());
                    if (existingQuestionCount > 0) {
                        userAnswerRepository.nullifySelectedAnswerReferences(quiz.getId());
                        userAnswerRepository.deleteByQuestionQuizId(quiz.getId());
                        questionRepository.deleteByQuizId(quiz.getId());
                    }
                    return null;
                });
                
                org.example.dto.request.generation.QuestionGenerationRequest genRequest =
                    new org.example.dto.request.generation.QuestionGenerationRequest(
                        quiz.getId(),
                        request.prompt(),
                        request.materials(),
                        request.questionNumber(),
                        questionCountForGeneration,
                        resolveDefaultQuestionType(quiz.getDefaultQuestionType())
                    );
                
                questionGenerationService.generateQuizQuestionsKafka(genRequest);
            } catch (Exception e) {
                try {
                    deleteQuiz(new DeleteQuizRequest(quiz.getId(), creator.getId()));
                } catch (Exception cleanupFailure) {
                    e.addSuppressed(cleanupFailure);
                }
                throw new GenerationRetryableException(
                        "Не удалось запустить генерацию квиза. Попробуйте ещё раз", e);
            }
        }

        return new QuizResponseDTO(
                quiz.getId(),
                quiz.getName(),
                "created",
                toLocalDateTime(quiz.getCreatedAt()),
                String.valueOf(quiz.getId())
        );
    }

    public QuizSearchResponse searchPublicQuizzes(QuizSearchRequest request) {
        Pageable pageable = createPageable(request.page(), request.size(), request.sortBy(), request.ascending());
        
        String sortBy = request.sortBy() != null ? request.sortBy().trim().toLowerCase() : "";
        String query = request.query() != null ? request.query().trim() : "";

        if (!query.isEmpty()) {
            try {
                Long quizId = Long.parseLong(query);
                var quizById = quizRepository.findPublicById(quizId);
                if (quizById.isPresent()) {
                    return new QuizSearchResponse(List.of(QuizMapper.fromEntity(quizById.get())), 0, 1, 1L);
                }
            } catch (NumberFormatException ignored) {
            }
        }

        Page<Quiz> page;
        if ("attempts".equals(sortBy) || "completedattempts".equals(sortBy)) {
            page = Boolean.TRUE.equals(request.ascending())
                    ? quizRepository.searchPublicQuizzesOrderByCompletedAttemptsAsc(query, pageable)
                    : quizRepository.searchPublicQuizzesOrderByCompletedAttemptsDesc(query, pageable);
        } else if (query.isEmpty()) {
            page = quizRepository.findByIsPrivateFalse(pageable);
        } else {
            page = quizRepository.searchPublicQuizzes(query, pageable);
        }

        List<QuizDTO> content = page.getContent().stream()
                .map(QuizMapper::fromEntity)
                .collect(Collectors.toList());

        return new QuizSearchResponse(
                content,
                page.getNumber(),
                page.getTotalPages(),
                page.getTotalElements()
        );
    }

    public QuizDetailsDTO getQuiz(Long quizId, Long userId) {
        String cacheKey = String.format(QUIZ_CACHE_KEY, quizId);
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                QuizDetailsDTO dto = objectMapper.readValue(cached, QuizDetailsDTO.class);
                if (Boolean.TRUE.equals(dto.isPublic())) {
                    return dto;
                }
            }
        } catch (Exception e) {
            log.debug("Не удалось прочитать квиз {} из кэша", quizId, e);
        }

        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new IllegalArgumentException("Квиз не найден"));

        if (quiz.isPrivate()) {
            if (userId != null && quiz.getCreatedBy().getId().equals(userId)) {
            } else {
                throw new SecurityException("Доступ к приватному квизу запрещен");
            }
        }

        List<QuestionDTO> questions = questionRepository.findByQuizId(quizId).stream()
                .map(this::toQuestionDTO)
                .collect(Collectors.toList());

        List<QuizMaterial> materials = new ArrayList<>();
        if (quiz.isHasMaterial() && quiz.getMaterialUrl() != null) {
            String url = quiz.getMaterialUrl();
            String name = normalizedMaterialName(quiz.getMaterialOriginalName(), url, quizId);
            materials = List.of(new QuizMaterial(name,
                    "/api/quizzes/" + quizId + "/materials/download", null, null));
        }

        Integer timePerQuestionSeconds = quiz.getTimePerQuestion() != null ? (int) quiz.getTimePerQuestion().getSeconds() : null;
        Integer totalTimeSeconds = null;
        if (timePerQuestionSeconds != null && quiz.getQuestionNumber() != null && quiz.getQuestionNumber() > 0) {
            totalTimeSeconds = timePerQuestionSeconds * quiz.getQuestionNumber();
        }

        QuizDetailsDTO dto = new QuizDetailsDTO(
                quiz.getId(),
                quiz.getName(),
                quiz.getPrompt(),
                quiz.getCreatedBy().getLogin(),
                questions,
                materials,
                quiz.getQuestionNumber(),
                totalTimeSeconds,
                timePerQuestionSeconds,
                !quiz.isPrivate(),
                quiz.isStatic(),
                String.valueOf(quiz.getId()),
                toLocalDateTime(quiz.getCreatedAt()),
                resolveDefaultQuestionType(quiz.getDefaultQuestionType())
        );

        if (!quiz.isPrivate()) {
            try {
                redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(dto), QUIZ_CACHE_TTL);
            } catch (Exception e) {
                log.debug("Не удалось записать квиз {} в кэш", quizId, e);
            }
        }

        return dto;
    }

    private QuestionType resolveDefaultQuestionType(QuestionType defaultQuestionType) {
        return defaultQuestionType != null ? defaultQuestionType : QuestionType.SINGLE_CHOICE;
    }

    public boolean deleteQuiz(DeleteQuizRequest request) {
        if (!quizRepository.existsById(request.quizId())) {
            throw new IllegalArgumentException("Квиз не найден");
        }

        if (!quizRepository.isCreator(request.quizId(), request.userId())) {
            throw new SecurityException("Только создатель может удалить квиз");
        }

        Long quizId = request.quizId();
        List<org.example.model.UserQuizAttempt> attempts = attemptRepository.findByQuizId(quizId);
        for (org.example.model.UserQuizAttempt attempt : attempts) {
            userAnswerRepository.findByAttemptId(attempt.getId()).forEach(userAnswerRepository::delete);
        }
        attemptRepository.findByQuizId(quizId).forEach(attemptRepository::delete);
        generationSetRepository.findAllByQuizId(quizId).forEach(generationSetRepository::delete);
        multiplayerSessionRepository.findByQuizId(quizId).forEach(multiplayerSessionRepository::delete);
        questionRepository.deleteByQuizId(quizId);
        try {
            fileStorageService.deleteQuizMaterials(quizId);
        } catch (Exception e) {
            log.debug("Не удалось удалить материалы квиза {}", quizId, e);
        }
        quizRepository.deleteById(quizId);

        evictQuizCache(quizId);
        leaderboardService.evict(quizId);

        return true;
    }

    public QuizResponseDTO updateQuizMetadata(UpdateQuizRequest request) {
        Quiz quiz = quizRepository.findById(request.quizId())
                .orElseThrow(() -> new IllegalArgumentException("Квиз не найден"));

        if (!quizRepository.isCreator(request.quizId(), request.userId())) {
            throw new SecurityException("Только создатель может редактировать квиз");
        }

        if (request.name() != null) {
            String name = request.name().trim();
            if (!name.isEmpty()) {
                quiz.setName(name);
            }
        }

        if (request.questionNumber() != null) {
            long existingQuestionCount = questionRepository.countByQuizId(request.quizId());
            int maxQuestionNumber = existingQuestionCount > 0
                    ? (int) Math.min(existingQuestionCount, Integer.MAX_VALUE)
                    : 1;
            int questionNumber = Math.max(1, Math.min(request.questionNumber(), maxQuestionNumber));
            quiz.setQuestionNumber(questionNumber);
        }

        if (request.timeLimit() != null) {
            int timeLimit = Math.max(1, request.timeLimit());
            quiz.setTimePerQuestion(java.time.Duration.ofSeconds(timeLimit));
        }

        if (request.isStatic() != null) {
            quiz.setStatic(request.isStatic());
        }

        if (request.prompt() != null) {
            quiz.setPrompt(request.prompt().trim());
        }

        if (request.isPrivate() != null) {
            quiz.setPrivate(request.isPrivate());
        }

        if (request.defaultQuestionType() != null) {
            quiz.setDefaultQuestionType(request.defaultQuestionType());
        }

        quiz = quizRepository.save(quiz);
        evictQuizCache(quiz.getId());

        return new QuizResponseDTO(
                quiz.getId(),
                quiz.getName(),
                "updated",
                toLocalDateTime(quiz.getCreatedAt()),
                String.valueOf(quiz.getId())
        );
    }

    public void regenerateWithMaterial(Long quizId, String materialText) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new IllegalArgumentException("Квиз не найден"));

        userAnswerRepository.nullifySelectedAnswerReferences(quizId);
        userAnswerRepository.deleteByQuestionQuizId(quizId);
        questionRepository.deleteByQuizId(quizId);

        QuestionGenerationRequest genRequest = new QuestionGenerationRequest(
                quizId,
                quiz.getPrompt(),
                null,
                quiz.getQuestionNumber(),
                GENERATED_QUESTION_POOL_SIZE,
                resolveDefaultQuestionType(quiz.getDefaultQuestionType())
        );

        questionGenerationService.generateQuizQuestionsKafka(genRequest);

        evictQuizCache(quizId);
    }

    @Transactional(readOnly = true)
    public GenerationStatusResponse getGenerationStatus(Long quizId) {
        GenerationSet generationSet = generationSetRepository.findTopByQuizIdOrderByCreatedAtDesc(quizId)
                .orElse(null);
        if (generationSet == null) {
            return new GenerationStatusResponse(
                    quizId,
                    null,
                    "NOT_STARTED",
                    "Генерация для этого квиза ещё не запускалась.",
                    true,
                    false
            );
        }

        String status = generationSet.getStatus() != null ? generationSet.getStatus() : "UNKNOWN";
        boolean failed = STATUS_FAILED.equals(status);
        boolean finished = STATUS_READY.equals(status) || failed;
        String message;
        if (STATUS_READY.equals(status)) {
            message = "Квиз готов.";
        } else if (failed) {
            message = generationSet.getFailureMessage() != null && !generationSet.getFailureMessage().isBlank()
                    ? generationSet.getFailureMessage()
                    : "Не удалось сгенерировать вопросы для этого квиза.";
        } else {
            message = "Квиз генерируется. Это может занять несколько минут.";
        }

        return new GenerationStatusResponse(
                quizId,
                generationSet.getId(),
                status,
                message,
                finished,
                failed
        );
    }

    public void updateQuizMaterialUrl(Long quizId, String materialUrl) {
        updateQuizMaterialUrl(quizId, materialUrl, null);
    }

    @Transactional(readOnly = true)
    public String getQuizPrompt(Long quizId) {
        return quizRepository.findById(quizId)
                .orElseThrow(() -> new IllegalArgumentException("Квиз не найден"))
                .getPrompt();
    }

    public void updateQuizMaterialUrl(Long quizId, String materialUrl, String originalFilename) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new IllegalArgumentException("Квиз не найден"));
        
        quiz.setMaterialUrl(materialUrl);
        if (originalFilename != null && !originalFilename.isBlank()) {
            quiz.setMaterialOriginalName(sanitizeMaterialName(originalFilename));
        }
        quiz.setHasMaterial(true);
        quizRepository.save(quiz);
        evictQuizCache(quizId);
    }

    @Transactional(readOnly = true)
    public Path getQuizMaterialPath(Long quizId, Long userId) {
        return getQuizMaterialDownload(quizId, userId).path();
    }

    @Transactional(readOnly = true)
    public QuizMaterialDownload getQuizMaterialDownload(Long quizId, Long userId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new IllegalArgumentException("Квиз не найден"));
        if (quiz.isPrivate() && !quiz.getCreatedBy().getId().equals(userId)) {
            throw new SecurityException("Доступ к материалу запрещён");
        }
        if (!quiz.isHasMaterial() || quiz.getMaterialUrl() == null || quiz.getMaterialUrl().isBlank()) {
            throw new IllegalArgumentException("У квиза нет материала");
        }
        Path path = fileStorageService.resolveQuizMaterialUrl(quizId, quiz.getMaterialUrl());
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Материал не найден");
        }
        return new QuizMaterialDownload(
                path,
                normalizedMaterialName(quiz.getMaterialOriginalName(), quiz.getMaterialUrl(), quizId)
        );
    }

    public record QuizMaterialDownload(Path path, String originalFilename) {}

    private String normalizedMaterialName(String storedName, String materialUrl, Long quizId) {
        String candidate = sanitizeMaterialName(storedName);
        if (!candidate.isBlank()) {
            return candidate;
        }
        String extension = "";
        if (materialUrl != null) {
            int dot = materialUrl.lastIndexOf('.');
            int slash = materialUrl.lastIndexOf('/');
            if (dot > slash && dot < materialUrl.length() - 1) {
                extension = materialUrl.substring(dot);
            }
        }
        return "quiz-" + quizId + "-material" + extension;
    }

    private String sanitizeMaterialName(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }
        String normalized = filename.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        normalized = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        normalized = normalized.replaceAll("[\\r\\n\\\"]", "_").trim();
        if (normalized.isBlank() || ".".equals(normalized) || "..".equals(normalized)) {
            return "";
        }
        return normalized.length() > 255 ? normalized.substring(0, 255) : normalized;
    }

    public boolean removeQuestionFromQuiz(RemoveQuestionRequest request) {
        if (!quizRepository.existsById(request.quizId())) {
            throw new IllegalArgumentException("Квиз не найден");
        }

        if (!quizRepository.isCreator(request.quizId(), request.userId())) {
            throw new SecurityException("Только создатель может удалять вопросы");
        }

        org.example.model.Question question = questionRepository.findByIdAndQuizId(request.questionId(), request.quizId())
                .orElseThrow(() -> new IllegalArgumentException("Вопрос не найден"));

        questionRepository.delete(question);
        return true;
    }

    public LeaderboardDTO getQuizLeaderboard(Long quizId, Long userId) {
        return getQuizLeaderboard(quizId, userId, "solo");
    }

    public LeaderboardDTO getQuizLeaderboard(Long quizId, Long userId, String mode) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new IllegalArgumentException("Квиз не найден"));
        if (quiz.isPrivate() && !quiz.getCreatedBy().getId().equals(userId)) {
            throw new SecurityException("Доступ к приватному квизу запрещён");
        }

        String normalizedMode = "multiplayer".equalsIgnoreCase(mode) ? "multiplayer" : "solo";
        if ("multiplayer".equals(normalizedMode)) {
            LeaderboardDTO redisLeaderboard = leaderboardService.getLeaderboard(quizId, userId, normalizedMode);
            if (redisLeaderboard != null) {
                return redisLeaderboard;
            }
        }

        Pageable pageable = PageRequest.of(0, 10000);
        Page<org.example.model.UserQuizAttempt> attempts = "multiplayer".equals(normalizedMode)
                ? attemptRepository.findCompletedMultiplayerByQuizIdOrderByScoreDesc(quizId, pageable)
                : attemptRepository.findCompletedByQuizIdOrderByScoreDesc(quizId, pageable);
        java.util.Map<Long, org.example.model.UserQuizAttempt> bestAttemptsByUser = new java.util.HashMap<>();
        for (org.example.model.UserQuizAttempt attempt : attempts.getContent()) {
            if (attempt.getUser() == null) {
                continue;
            }
            Long attemptUserId = attempt.getUser().getId();
            org.example.model.UserQuizAttempt currentBest = bestAttemptsByUser.get(attemptUserId);
            if (currentBest == null || compareLeaderboardAttempts(attempt, currentBest, normalizedMode) < 0) {
                bestAttemptsByUser.put(attemptUserId, attempt);
            }
        }
        List<org.example.model.UserQuizAttempt> sortedAttempts = new ArrayList<>(bestAttemptsByUser.values());
        sortedAttempts.sort((a, b) -> compareLeaderboardAttempts(a, b, normalizedMode));
        if (sortedAttempts.size() > 100) {
            sortedAttempts = sortedAttempts.subList(0, 100);
        }

        List<LeaderboardEntry> entries = new ArrayList<>();
        int userPosition = -1;
        Long userScore = null;

        for (int i = 0; i < sortedAttempts.size(); i++) {
            org.example.model.UserQuizAttempt attempt = sortedAttempts.get(i);
            long timeSpent = getTimeSpentSeconds(attempt);
            int accuracyPercent = calculateAccuracyPercent(attempt);
            int attemptNumber = getCompletedAttemptNumber(attempt);

            entries.add(new LeaderboardEntry(
                    i + 1,
                    attempt.getUser().getLogin(),
                    getLeaderboardScore(attempt, normalizedMode),
                    accuracyPercent,
                    attemptNumber,
                    timeSpent
            ));

            if (attempt.getUser().getId().equals(userId)) {
                userPosition = i + 1;
                userScore = (long) getLeaderboardScore(attempt, normalizedMode);
            }
        }

        return new LeaderboardDTO(entries, userPosition, userScore != null ? userScore.intValue() : null);
    }

    private int compareAttempts(org.example.model.UserQuizAttempt a, org.example.model.UserQuizAttempt b) {
        int scoreCompare = Long.compare(
                getEffectiveBaseScore(b),
                getEffectiveBaseScore(a)
        );
        if (scoreCompare != 0) {
            return scoreCompare;
        }
        int accuracyCompare = Integer.compare(calculateAccuracyPercent(b), calculateAccuracyPercent(a));
        if (accuracyCompare != 0) {
            return accuracyCompare;
        }
        int attemptNumberCompare = Integer.compare(getCompletedAttemptNumber(a), getCompletedAttemptNumber(b));
        if (attemptNumberCompare != 0) {
            return attemptNumberCompare;
        }
        return Long.compare(getTimeSpentSeconds(a), getTimeSpentSeconds(b));
    }

    private int compareLeaderboardAttempts(org.example.model.UserQuizAttempt a,
                                           org.example.model.UserQuizAttempt b,
                                           String mode) {
        return "multiplayer".equals(mode) ? compareMultiplayerAttempts(a, b) : compareAttempts(a, b);
    }

    public LeaderboardDTO getQuizSessionLeaderboard(Long quizId, Long userId, String sessionId) {
        if (!quizRepository.existsById(quizId)) {
            throw new IllegalArgumentException("Квиз не найден");
        }
        if (sessionId == null || sessionId.isBlank()) {
            return new LeaderboardDTO(List.of(), -1, null);
        }
        Pageable pageable = PageRequest.of(0, 10000);
        List<org.example.model.UserQuizAttempt> attempts = new ArrayList<>(
                attemptRepository.findCompletedByQuizIdAndSessionId(quizId, sessionId, pageable).getContent()
        );
        attempts.sort(this::compareMultiplayerAttempts);

        List<LeaderboardEntry> entries = new ArrayList<>();
        int userPosition = -1;
        Integer userScore = null;
        for (int i = 0; i < attempts.size(); i++) {
            org.example.model.UserQuizAttempt attempt = attempts.get(i);
            if (attempt.getUser() == null) {
                continue;
            }
            int score = attempt.getScore() != null ? attempt.getScore().intValue() : 0;
            entries.add(new LeaderboardEntry(
                    i + 1,
                    attempt.getUser().getLogin(),
                    score,
                    calculateAccuracyPercent(attempt),
                    getCompletedAttemptNumber(attempt),
                    getTimeSpentSeconds(attempt)
            ));
            if (attempt.getUser().getId().equals(userId)) {
                userPosition = i + 1;
                userScore = score;
            }
        }
        return new LeaderboardDTO(entries, userPosition, userScore);
    }

    private int compareMultiplayerAttempts(org.example.model.UserQuizAttempt a, org.example.model.UserQuizAttempt b) {
        int scoreCompare = Long.compare(
                b.getScore() != null ? b.getScore() : 0L,
                a.getScore() != null ? a.getScore() : 0L
        );
        if (scoreCompare != 0) {
            return scoreCompare;
        }
        int accuracyCompare = Integer.compare(calculateAccuracyPercent(b), calculateAccuracyPercent(a));
        if (accuracyCompare != 0) {
            return accuracyCompare;
        }
        int attemptNumberCompare = Integer.compare(getCompletedAttemptNumber(a), getCompletedAttemptNumber(b));
        if (attemptNumberCompare != 0) {
            return attemptNumberCompare;
        }
        return Long.compare(getTimeSpentSeconds(a), getTimeSpentSeconds(b));
    }

    private int getLeaderboardScore(org.example.model.UserQuizAttempt attempt, String mode) {
        if ("multiplayer".equals(mode)) {
            return attempt.getScore() != null ? attempt.getScore().intValue() : 0;
        }
        return (int) getEffectiveBaseScore(attempt);
    }

    private long getEffectiveBaseScore(org.example.model.UserQuizAttempt attempt) {
        if (attempt == null) {
            return 0L;
        }
        if (attempt.getBaseScore() != null) {
            return attempt.getBaseScore();
        }
        long score = attempt.getScore() != null ? attempt.getScore() : 0L;
        return score - (attempt.getCatStakeBonus() != null ? attempt.getCatStakeBonus() : 0);
    }

    private int getCompletedAttemptNumber(org.example.model.UserQuizAttempt attempt) {
        if (attempt.getUser() == null || attempt.getQuiz() == null || attempt.getId() == null) {
            return Integer.MAX_VALUE;
        }
        long attemptNumber = attemptRepository.countCompletedByUserIdAndQuizIdUpToAttemptId(
                attempt.getUser().getId(),
                attempt.getQuiz().getId(),
                attempt.getId()
        );
        return attemptNumber > 0 ? (int) attemptNumber : Integer.MAX_VALUE;
    }

    private long getTimeSpentSeconds(org.example.model.UserQuizAttempt attempt) {
        if (attempt.getStartTime() == null || attempt.getFinishTime() == null) {
            return Long.MAX_VALUE;
        }
        return java.time.Duration.between(attempt.getStartTime(), attempt.getFinishTime()).getSeconds();
    }

    private Integer calculateAccuracyPercent(org.example.model.UserQuizAttempt attempt) {
        if (attempt.getAccuracyPercent() != null) {
            return attempt.getAccuracyPercent();
        }
        List<org.example.model.UserAnswer> answers = userAnswerRepository.findByAttemptId(attempt.getId());
        int totalQuestions = attempt.getQuiz().getQuestionNumber() != null
                ? attempt.getQuiz().getQuestionNumber()
                : (int) questionRepository.countByQuizId(attempt.getQuiz().getId());
        if (totalQuestions <= 0) {
            return 0;
        }
        // Для старых попыток применяем те же правила, что и для новых завершённых,
        // включая частичные баллы за вопросы с несколькими вариантами.
        double accuracySum = answers.stream()
                .mapToDouble(answer -> answer.getAccuracyRatio() != null
                        ? answer.getAccuracyRatio()
                        : Boolean.TRUE.equals(answer.getIsCorrect()) ? 1.0 : 0.0)
                .sum();
        return (int) Math.round(accuracySum * 100.0 / totalQuestions);
    }

    public QuizResponseDTO copyQuiz(Long quizId, Long newCreatorId) {
        Quiz originalQuiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new IllegalArgumentException("Квиз не найден"));

        if (originalQuiz.isPrivate() && !originalQuiz.getCreatedBy().getId().equals(newCreatorId)) {
            throw new SecurityException("Доступ к приватному квизу запрещён");
        }

        User newCreator = userRepository.findById(newCreatorId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        Quiz copiedQuiz = new Quiz();
        copiedQuiz.setName(originalQuiz.getName() + " (копия)");
        copiedQuiz.setPrompt(originalQuiz.getPrompt());
        copiedQuiz.setCreatedBy(newCreator);
        copiedQuiz.setHasMaterial(false);
        copiedQuiz.setMaterialUrl(null);
        copiedQuiz.setMaterialOriginalName(null);
        copiedQuiz.setQuestionNumber(originalQuiz.getQuestionNumber());
        copiedQuiz.setTimePerQuestion(originalQuiz.getTimePerQuestion());
        copiedQuiz.setPrivate(originalQuiz.isPrivate());
        copiedQuiz.setStatic(originalQuiz.isStatic());
        copiedQuiz.setDefaultQuestionType(originalQuiz.getDefaultQuestionType());
        copiedQuiz.setCreatedAt(Instant.now());

        copiedQuiz = quizRepository.save(copiedQuiz);

        List<org.example.model.Question> originalQuestions = questionRepository.findByQuizId(quizId);
        Long copiedGenerationSetId = null;
        if (!originalQuestions.isEmpty()) {
            GenerationSet copiedSet = new GenerationSet();
            copiedSet.setQuiz(copiedQuiz);
            copiedSet.setPrompt(copiedQuiz.getPrompt());
            copiedSet.setStatus(STATUS_READY);
            copiedSet.setGeneratedCount(originalQuestions.size());
            copiedSet.setValidCount(originalQuestions.size());
            copiedSet.setDuplicateCount(0);
            copiedSet.setFinalCount(originalQuestions.size());
            copiedSet.setCreatedAt(Instant.now());
            copiedGenerationSetId = generationSetRepository.save(copiedSet).getId();
        }

        for (org.example.model.Question originalQuestion : originalQuestions) {
            org.example.model.Question copiedQuestion = new org.example.model.Question();
            copiedQuestion.setQuiz(copiedQuiz);
            copiedQuestion.setText(originalQuestion.getText());
            copiedQuestion.setType(originalQuestion.getType());
            copiedQuestion.setExplanation(originalQuestion.getExplanation());
            copiedQuestion.setImage(originalQuestion.getImage() != null
                    ? originalQuestion.getImage().clone() : null);
            copiedQuestion.setGenerationSetId(copiedGenerationSetId);
            for (org.example.model.AnswerOption originalOption :
                    answerOptionRepository.findByQuestionId(originalQuestion.getId())) {
                org.example.model.AnswerOption copiedOption = new org.example.model.AnswerOption();
                copiedOption.setQuestion(copiedQuestion);
                copiedOption.setText(originalOption.getText());
                copiedOption.setCorrect(originalOption.isCorrect());
                copiedOption.setNominal(originalOption.getNominal());
                copiedQuestion.getAnswerOptions().add(copiedOption);
            }
            questionRepository.save(copiedQuestion);
        }

        if (originalQuiz.getMaterialUrl() != null && !originalQuiz.getMaterialUrl().isBlank()) {
            Long copiedId = copiedQuiz.getId();
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            try {
                                fileStorageService.deleteQuizMaterials(copiedId);
                            } catch (IOException e) {
                                log.warn("Не удалось удалить материал незавершённой копии квиза {}", copiedId, e);
                            }
                        }
                    }
                });
            }
            try {
                copiedQuiz.setMaterialUrl(fileStorageService.copyQuizMaterial(
                        quizId, copiedId, originalQuiz.getMaterialUrl()));
                copiedQuiz.setMaterialOriginalName(
                        sanitizeMaterialName(originalQuiz.getMaterialOriginalName()));
                copiedQuiz.setHasMaterial(true);
                quizRepository.save(copiedQuiz);
            } catch (IOException e) {
                throw new UncheckedIOException("Не удалось скопировать материал квиза", e);
            }
        }

        return new QuizResponseDTO(
                copiedQuiz.getId(),
                copiedQuiz.getName(),
                "copied",
                toLocalDateTime(copiedQuiz.getCreatedAt()),
                String.valueOf(copiedQuiz.getId())
        );
    }

    public QuizDetailsDTO getQuizById(Long quizId, Long userId) {
        return getQuiz(quizId, userId);
    }

    private Pageable createPageable(Integer page, Integer size, String sortBy, Boolean ascending) {
        int pageNumber = page != null && page >= 0 ? page : 0;
        int pageSize = size != null && size > 0 ? size : 10;
        
        Sort.Direction direction = (ascending != null && ascending) ? 
                Sort.Direction.ASC : Sort.Direction.DESC;
        
        String sortField = "createdAt";
        if (sortBy != null) {
            switch (sortBy.toLowerCase()) {
                case "name":
                    sortField = "name";
                    break;
                case "questions":
                case "question_count":
                case "questioncount":
                    sortField = "questionNumber";
                    break;
                case "attempts":
                case "completedattempts":
                    sortField = "createdAt";
                    break;
                case "created":
                case "created_at":
                case "createdat":
                    sortField = "createdAt";
                    break;
                case "popularity":
                    sortField = "createdAt";
                    break;
                default:
                    sortField = "createdAt";
            }
        }
        
        Sort sort = Sort.by(direction, sortField);
        return PageRequest.of(pageNumber, pageSize, sort);
    }

    private QuestionDTO toQuestionDTO(org.example.model.Question question) {
        List<org.example.model.AnswerOption> options = answerOptionRepository.findByQuestionId(question.getId());
        List<org.example.dto.common.AnswerOption> dtoOptions = options.stream()
                .map(opt -> new org.example.dto.common.AnswerOption(opt.getId(), opt.getText(), opt.getNominal()))
                .collect(Collectors.toList());

        Integer timeLimit = null;
        if (question.getQuiz().getTimePerQuestion() != null) {
            timeLimit = (int) question.getQuiz().getTimePerQuestion().getSeconds();
        }

        return new QuestionDTO(
                question.getId(),
                question.getText(),
                dtoOptions,
                question.getType(),
                timeLimit,
                null,
                question.getExplanation(),
                null,
                null,
                0,
                toLocalDateTime(question.getQuiz().getCreatedAt())
        );
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant != null
                ? LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
                : null;
    }

    private void evictQuizCache(Long quizId) {
        try {
            redisTemplate.delete(String.format(QUIZ_CACHE_KEY, quizId));
        } catch (Exception e) {
            log.debug("Не удалось инвалидировать кэш квиза {}", quizId, e);
        }
    }
}

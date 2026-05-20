package org.example.service;

import org.example.dto.common.AnswerOption;
import org.example.dto.request.attempt.StartAttemptRequest;
import org.example.dto.request.attempt.SubmitAnswerRequest;
import org.example.dto.response.attempt.AnswerResponse;
import org.example.dto.response.attempt.AttemptPageProgress;
import org.example.dto.response.attempt.AttemptResponse;
import org.example.dto.response.attempt.QuizResultDTO;
import org.example.dto.response.quiz.QuestionDTO;
import org.example.model.*;
import org.example.repository.*;
import org.example.metrics.MetricsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Random;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
public class AttemptService {
  private final Map<Long, AttemptState> attemptStates = new ConcurrentHashMap<>();
    private final UserQuizAttemptRepository attemptRepository;
    private final QuizRepository quizRepository;
    private final UserRepository userRepository;
    private final QuestionRepository questionRepository;
    private final AnswerOptionRepository answerOptionRepository;
    private final UserAnswerRepository userAnswerRepository;
    private final org.example.repository.MultiplayerSessionRepository multiplayerSessionRepository;
    private final org.example.repository.AttemptQuestionRepository attemptQuestionRepository;
    private final LeaderboardService leaderboardService;
    private final MetricsService metricsService;

    @Autowired
    public AttemptService(
            UserQuizAttemptRepository attemptRepository,
            QuizRepository quizRepository,
            UserRepository userRepository,
            QuestionRepository questionRepository,
            AnswerOptionRepository answerOptionRepository,
            UserAnswerRepository userAnswerRepository,
            org.example.repository.MultiplayerSessionRepository multiplayerSessionRepository,
            org.example.repository.AttemptQuestionRepository attemptQuestionRepository,
            LeaderboardService leaderboardService,
            MetricsService metricsService) {
        this.attemptRepository = attemptRepository;
        this.quizRepository = quizRepository;
        this.userRepository = userRepository;
        this.questionRepository = questionRepository;
        this.answerOptionRepository = answerOptionRepository;
        this.userAnswerRepository = userAnswerRepository;
        this.multiplayerSessionRepository = multiplayerSessionRepository;
        this.attemptQuestionRepository = attemptQuestionRepository;
        this.leaderboardService = leaderboardService;
        this.metricsService = metricsService;
    }

    private static class AttemptState {
        Long attemptId;
        Long userId;
        Long quizId;
        List<Long> questionIds;
        int currentQuestionIndex;
        Map<Long, Long> answers;
        Map<Long, Boolean> answerResults;
        Instant startTime;
        double score;
        double baseScore;

        Map<Long, Map<Long, BigDecimal>> hundredToOneNominalsByQuestionId;

        Integer catQuestionIndex;
        Integer stakeForCurrentQuestion;
    }

    public AttemptResponse startQuizAttempt(StartAttemptRequest request) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> {
                    return new IllegalArgumentException("Пользователь не найден");
                });

        Quiz quiz = quizRepository.findById(request.quizId())
                .orElseThrow(() -> {
                    return new IllegalArgumentException("Квиз не найден");
                });

        if (quiz.isPrivate()) {
            if (!quizRepository.isCreator(request.quizId(), request.userId())) {
                throw new SecurityException("Доступ к приватному квизу запрещён");
            }
        }

        List<Question> allQuestions = questionRepository.findByQuizId(request.quizId());
        if (allQuestions.isEmpty()) {
            throw new IllegalStateException("Квиз не содержит вопросов");
        }

        UserQuizAttempt attempt;
        Integer resolvedCatQuestionIndex = request.catQuestionIndex();

        if (request.sessionId() != null && !request.sessionId().isEmpty()) {
            attempt = attemptRepository.findTopByUserIdAndQuizIdAndSessionIdOrderByIdDesc(
                    request.userId(), request.quizId(), request.sessionId());

            if (attempt == null) {
                attempt = new UserQuizAttempt();
                attempt.setUser(user);
                attempt.setQuiz(quiz);
                attempt.setStartTime(Instant.now());
                attempt.setCompleted(false);
                attempt.setScore(null);
                attempt.setSessionId(request.sessionId());
                attempt = attemptRepository.save(attempt);
                selectQuestionsForAttempt(attempt, quiz, allQuestions);
            } else {
                if (attemptQuestionRepository.findByAttemptIdOrderByQuestionOrder(attempt.getId()).isEmpty()) {
                    selectQuestionsForAttempt(attempt, quiz, allQuestions);
                }
                if (attempt.isCompleted()) {
                    throw new IllegalStateException("ATTEMPT_COMPLETED:" + attempt.getId());
                }
            }
        } else {
            attempt = attemptRepository.findTopByUserIdAndQuizIdAndSessionIdIsNullAndIsCompletedFalseOrderByIdDesc(
                    request.userId(), request.quizId());
            if (attempt == null) {
                attempt = new UserQuizAttempt();
                attempt.setUser(user);
                attempt.setQuiz(quiz);
                attempt.setStartTime(Instant.now());
                attempt.setCompleted(false);
                attempt.setScore(null);
                attempt.setSessionId(null);
                attempt = attemptRepository.save(attempt);
                selectQuestionsForAttempt(attempt, quiz, allQuestions);
            } else if (attemptQuestionRepository.findByAttemptIdOrderByQuestionOrder(attempt.getId()).isEmpty()) {
                selectQuestionsForAttempt(attempt, quiz, allQuestions);
            }
        }

        if (request.sessionId() != null && !request.sessionId().isBlank()) {
            var sessionOpt = multiplayerSessionRepository.findBySessionId(request.sessionId());
            if (sessionOpt.isPresent()) {
                var session = sessionOpt.get();
                if (session.getCatQuestionIndex() != null) {
                    resolvedCatQuestionIndex = session.getCatQuestionIndex();
                } else {
                    int totalQuestions = attemptQuestionRepository
                            .findByAttemptIdOrderByQuestionOrder(attempt.getId())
                            .size();
                    if (totalQuestions > 0) {
                        int lastSegmentStart = Math.max(0, totalQuestions - Math.max(1, totalQuestions / 3));
                        int catIndex = lastSegmentStart + new Random().nextInt(totalQuestions - lastSegmentStart);
                        session.setCatQuestionIndex(catIndex);
                        multiplayerSessionRepository.save(session);
                        resolvedCatQuestionIndex = catIndex;
                    }
                }
            }
        }

        AttemptState existingState = attemptStates.get(attempt.getId());
        if (existingState == null) {
            AttemptState st = new AttemptState();
            st.attemptId = attempt.getId();
            st.userId = request.userId();
            st.quizId = quiz.getId();
            st.hundredToOneNominalsByQuestionId = new ConcurrentHashMap<>();
            st.score = attempt.getScore() != null ? attempt.getScore() : 0.0;
            st.baseScore = getEffectiveBaseScore(attempt);
            st.catQuestionIndex = resolvedCatQuestionIndex;
            st.stakeForCurrentQuestion = null;
            attemptStates.put(attempt.getId(), st);
        } else if (resolvedCatQuestionIndex != null) {
            existingState.catQuestionIndex = resolvedCatQuestionIndex;
        }

        QuestionDTO currentQuestion = getNextQuestion(attempt.getId());
        if (currentQuestion == null) {
            throw new IllegalStateException("ATTEMPT_COMPLETED:" + attempt.getId());
        }

        List<AttemptQuestion> attemptQuestions = attemptQuestionRepository.findByAttemptIdOrderByQuestionOrder(attempt.getId());
        int totalQuestions = attemptQuestions.size();
        int questionsRemaining = totalQuestions - userAnswerRepository.findByAttemptId(attempt.getId()).size();
        Integer timeRemaining = getRemainingSeconds(attempt, currentQuestion);

        return new AttemptResponse(
                attempt.getId(),
                quiz.getId(),
                quiz.getName(),
                currentQuestion,
                questionsRemaining,
                totalQuestions,
                timeRemaining,
                toEpochMillis(attempt.getCurrentQuestionDeadlineAt())
        );
    }
    public QuestionDTO getNextQuestion(Long attemptId) {
        return getNextQuestionInternal(attemptId, true);
    }
    public AttemptPageProgress getAttemptPageProgress(Long attemptId) {
        UserQuizAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("Попытка не найдена"));
        if (attempt.isCompleted()) {
            throw new IllegalStateException("Попытка уже завершена");
        }

        List<AttemptQuestion> attemptQuestions = attemptQuestionRepository.findByAttemptIdOrderByQuestionOrder(attemptId);
        int total = attemptQuestions.size();
        int answered = userAnswerRepository.findByAttemptId(attemptId).size();
        int remaining = Math.max(0, total - answered);
        int seconds = getDefaultTimePerQuestionSeconds(attempt.getQuiz());

        return new AttemptPageProgress(
                total,
                remaining,
                attempt.getCurrentQuestionDeadlineAt() != null ? getRemainingSeconds(attempt, null) : seconds,
                toEpochMillis(attempt.getCurrentQuestionDeadlineAt())
        );
    }
    public AnswerResponse submitAnswer(SubmitAnswerRequest request) {
        UserQuizAttempt attempt = attemptRepository.findById(request.attemptId())
                .orElseThrow(() -> new IllegalArgumentException("Попытка не найдена"));

        if (attempt.isCompleted()) {
            throw new IllegalStateException("Попытка уже завершена");
        }

        Long questionId = request.questionId();
        if (questionId == null) {
            questionId = attempt.getCurrentQuestionId();
        }
        if (questionId == null) {
            AttemptQuestion pendingQuestion = findNextPendingAttemptQuestion(request.attemptId());
            if (pendingQuestion == null || pendingQuestion.getQuestion() == null) {
                throw new IllegalStateException("Нет активных вопросов");
            }
            questionId = pendingQuestion.getQuestion().getId();
        }

        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Вопрос не найден"));

        if (!question.getQuiz().getId().equals(attempt.getQuiz().getId())) {
            throw new IllegalArgumentException("Вопрос не принадлежит этому квизу");
        }
        if (userAnswerRepository.existsByAttemptIdAndQuestionId(request.attemptId(), questionId)) {
            throw new IllegalStateException("Вопрос уже отвечен");
        }
        if (attempt.getCurrentQuestionId() != null && !attempt.getCurrentQuestionId().equals(questionId)) {
            throw new IllegalStateException("Сейчас активен другой вопрос");
        }

        AttemptState catCheck = attemptStates.get(request.attemptId());
        if (catCheck != null && catCheck.catQuestionIndex != null && catCheck.stakeForCurrentQuestion == null) {
            int qIdx = getQuestionIndexInAttempt(request.attemptId(), questionId);
            if (qIdx == catCheck.catQuestionIndex) {
                throw new IllegalStateException("Сначала необходимо сделать ставку (submitStake)");
            }
        }

        Instant answeredAt = Instant.now();
        boolean timedOut = isTimedOut(attempt, questionId, answeredAt);
        List<Long> selectedIds = timedOut ? List.of() : request.getEffectiveSelectedIds();
        Boolean isCorrect;
        Long correctAnswerId;
        int scoreEarned = 0;
        double scoreEarnedDouble = 0.0;
        double questionAccuracyRatio = 0.0;
        org.example.model.AnswerOption selectedOption = null;

        List<org.example.model.AnswerOption> allOptions = answerOptionRepository.findByQuestionId(questionId);
        java.util.Set<Long> correctIds = allOptions.stream()
                .filter(org.example.model.AnswerOption::isCorrect)
                .map(org.example.model.AnswerOption::getId)
                .collect(java.util.stream.Collectors.toSet());
        correctAnswerId = correctIds.isEmpty() ? null : correctIds.iterator().next();

        if (!selectedIds.isEmpty()) {
            selectedOption = answerOptionRepository.findById(selectedIds.get(0)).orElse(null);

            if (question.getType() == QuestionType.MULTIPLE_CHOICE) {
                int a = (int) selectedIds.stream().filter(correctIds::contains).count();
                int b = (int) selectedIds.stream().filter(id -> !correctIds.contains(id)).count();
                int c = correctIds.size();
                double points = c > 0 ? 2.0 * Math.max(a - b, 0) / c : 0;
                questionAccuracyRatio = c > 0 ? (double) Math.max(a - b, 0) / c : 0.0;
                scoreEarned = (int) Math.round(points);
                scoreEarnedDouble = points;
                java.util.Set<Long> selectedIdSet = new java.util.HashSet<>(selectedIds);
                isCorrect = selectedIdSet.equals(correctIds);
            } else if (question.getType() == QuestionType.HUNDRED_TO_ONE) {
                java.util.Map<Long, BigDecimal> nominals = ensureHundredToOneNominals(request.attemptId(), question);
                BigDecimal sum = BigDecimal.ZERO;
                for (Long selectedId : selectedIds) {
                    if (selectedId == null) {
                        continue;
                    }
                    BigDecimal nominal = nominals.get(selectedId);
                    if (nominal != null) {
                        sum = sum.add(nominal);
                    }
                }
                scoreEarned = sum.setScale(0, RoundingMode.HALF_UP).intValue();
                scoreEarnedDouble = sum.doubleValue();
                isCorrect = scoreEarned > 0;
                questionAccuracyRatio = Boolean.TRUE.equals(isCorrect) ? 1.0 : 0.0;
            } else {
                isCorrect = selectedIds.size() == 1 && correctIds.contains(selectedIds.get(0));
                scoreEarned = isCorrect ? calculateScore(question, attempt) : 0;
                scoreEarnedDouble = scoreEarned;
                questionAccuracyRatio = Boolean.TRUE.equals(isCorrect) ? 1.0 : 0.0;
            }
        } else {
            isCorrect = false;
        }

        if (Boolean.TRUE.equals(isCorrect)) {
            metricsService.recordCorrectAnswer();
        } else {
            metricsService.recordIncorrectAnswer();
        }

        UserAnswer userAnswer = new UserAnswer();
        userAnswer.setAttempt(attempt);
        userAnswer.setQuestion(question);
        userAnswer.setSelectedAnswer(selectedOption);
        userAnswer.setIsCorrect(isCorrect);
        userAnswer.setAccuracyRatio(questionAccuracyRatio);
        userAnswerRepository.save(userAnswer);

        clearCurrentQuestionState(attempt);

        AttemptState st = attemptStates.get(request.attemptId());
        if (st != null) {
            st.score += scoreEarnedDouble;
            st.baseScore += scoreEarnedDouble;
            if (st.catQuestionIndex != null && st.stakeForCurrentQuestion != null) {
                int qIndex = getQuestionIndexInAttempt(request.attemptId(), questionId);
                if (qIndex == st.catQuestionIndex) {
                    int stake = st.stakeForCurrentQuestion;
                    int stakeBonus;
                    if (Boolean.TRUE.equals(isCorrect)) {
                        st.score += stake;
                        scoreEarned += stake;
                        stakeBonus = stake;
                    } else {
                        st.score -= stake;
                        scoreEarned -= stake;
                        stakeBonus = -stake;
                    }
                    attempt.setCatStake(stake);
                    attempt.setCatStakeBonus(stakeBonus);
                    st.stakeForCurrentQuestion = null;
                }
            }
            attempt.setScore(Math.round(st.score));
            attempt.setBaseScore(Math.round(st.baseScore));
            attemptRepository.save(attempt);
        } else {
            Long currentScore = attempt.getScore() != null ? attempt.getScore() : 0L;
            attempt.setScore(currentScore + scoreEarned);
            Long currentBaseScore = attempt.getBaseScore() != null ? attempt.getBaseScore() : currentScore;
            int baseDelta = scoreEarned - (attempt.getCatStakeBonus() != null ? attempt.getCatStakeBonus() : 0);
            attempt.setBaseScore(currentBaseScore + baseDelta);
            attemptRepository.save(attempt);
        }

        QuestionDTO nextQuestion = peekNextQuestion(request.attemptId());
        String explanation = question.getExplanation() != null
                ? question.getExplanation()
                : "Объяснение отсутствует";
        java.util.List<Long> correctAnswerIds = correctIds.isEmpty()
                ? java.util.List.of()
                : correctIds.stream().sorted().toList();

        return new AnswerResponse(
                isCorrect,
                explanation,
                correctAnswerId,
                correctAnswerIds,
                scoreEarned,
                nextQuestion,
                attempt.getQuiz().getId()
        );
    }
    private QuestionDTO peekNextQuestion(Long attemptId) {
        return getNextQuestionInternal(attemptId, false);
    }

    private QuestionDTO getNextQuestionInternal(Long attemptId, boolean activateQuestion) {
        UserQuizAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("Попытка не найдена"));

        if (attempt.isCompleted()) {
            throw new IllegalStateException("Попытка уже завершена");
        }

        AttemptQuestion pendingQuestion = findNextPendingAttemptQuestion(attemptId);
        if (pendingQuestion == null || pendingQuestion.getQuestion() == null) {
            clearCurrentQuestionState(attempt);
            return null;
        }

        Question question = pendingQuestion.getQuestion();
        int questionIndex = pendingQuestion.getQuestionOrder() != null ? pendingQuestion.getQuestionOrder() : -1;

        AttemptState st = attemptStates.get(attemptId);
        if (st != null && st.catQuestionIndex != null
                && questionIndex == st.catQuestionIndex
                && st.stakeForCurrentQuestion == null) {
            clearCurrentQuestionState(attempt);
            Integer timeLimit = null;
            Quiz quiz = question.getQuiz();
            if (quiz != null && quiz.getTimePerQuestion() != null) {
                timeLimit = (int) quiz.getTimePerQuestion().getSeconds();
            }
            return new QuestionDTO(
                    question.getId(),
                    null,
                    List.of(),
                    question.getType(),
                    timeLimit,
                    null, null, null, null, 0,
                    quiz != null ? toLocalDateTime(quiz.getCreatedAt()) : null,
                    true
            );
        }

        if (activateQuestion) {
            activateCurrentQuestion(attempt, question);
        }
        return toQuestionDTO(attemptId, question);
    }

    private AttemptQuestion findNextPendingAttemptQuestion(Long attemptId) {
        List<AttemptQuestion> attemptQuestions = attemptQuestionRepository.findByAttemptIdOrderByQuestionOrder(attemptId);
        if (attemptQuestions.isEmpty()) {
            return null;
        }

        List<Long> answeredQuestionIds = userAnswerRepository.findByAttemptId(attemptId).stream()
                .map(answer -> answer.getQuestion() != null ? answer.getQuestion().getId() : null)
                .filter(id -> id != null)
                .collect(Collectors.toList());

        for (AttemptQuestion attemptQuestion : attemptQuestions) {
            Question question = attemptQuestion.getQuestion();
            if (question != null && !answeredQuestionIds.contains(question.getId())) {
                return attemptQuestion;
            }
        }
        return null;
    }

    private void activateCurrentQuestion(UserQuizAttempt attempt, Question question) {
        if (question == null) {
            return;
        }

        attempt.setCurrentQuestionId(question.getId());
        attempt.setCurrentQuestionStartedAt(Instant.now());
        int seconds = getDefaultTimePerQuestionSeconds(attempt.getQuiz());
        attempt.setCurrentQuestionDeadlineAt(attempt.getCurrentQuestionStartedAt().plusSeconds(seconds));
        attemptRepository.save(attempt);
    }

    private void clearCurrentQuestionState(UserQuizAttempt attempt) {
        if (attempt.getCurrentQuestionId() == null
                && attempt.getCurrentQuestionStartedAt() == null
                && attempt.getCurrentQuestionDeadlineAt() == null) {
            return;
        }
        attempt.setCurrentQuestionId(null);
        attempt.setCurrentQuestionStartedAt(null);
        attempt.setCurrentQuestionDeadlineAt(null);
        attemptRepository.save(attempt);
    }

    private boolean isTimedOut(UserQuizAttempt attempt, Long questionId, Instant now) {
        return questionId != null
                && questionId.equals(attempt.getCurrentQuestionId())
                && attempt.getCurrentQuestionDeadlineAt() != null
                && now.isAfter(attempt.getCurrentQuestionDeadlineAt());
    }

    private Integer getRemainingSeconds(UserQuizAttempt attempt, QuestionDTO currentQuestion) {
        if (currentQuestion != null && Boolean.TRUE.equals(currentQuestion.isCatInBagStakeScreen())) {
            return getDefaultTimePerQuestionSeconds(attempt.getQuiz());
        }
        if (attempt.getCurrentQuestionDeadlineAt() == null) {
            return getDefaultTimePerQuestionSeconds(attempt.getQuiz());
        }
        long remainingMillis = Duration.between(Instant.now(), attempt.getCurrentQuestionDeadlineAt()).toMillis();
        if (remainingMillis <= 0) {
            return 0;
        }
        return (int) Math.ceil(remainingMillis / 1000.0);
    }

    private int getDefaultTimePerQuestionSeconds(Quiz quiz) {
        return quiz != null && quiz.getTimePerQuestion() != null
                ? (int) quiz.getTimePerQuestion().getSeconds()
                : 60;
    }

    private Long toEpochMillis(Instant instant) {
        return instant != null ? instant.toEpochMilli() : null;
    }
    public QuizResultDTO finishQuizAttempt(Long attemptId) {
        UserQuizAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("Попытка не найдена"));

        if (attempt.getSessionId() != null && !attempt.getSessionId().isBlank()
                && attempt.getStartTime() == null && !attempt.isCompleted()) {
            throw new IllegalStateException("Мультиплеерная попытка ещё не началась");
        }

        if (attempt.isCompleted()) {
            List<AttemptQuestion> attemptQuestions = attemptQuestionRepository.findByAttemptIdOrderByQuestionOrder(attemptId);
            int totalQuestions = attemptQuestions.size();
            int correctAnswers = (int) userAnswerRepository.countByAttemptIdAndIsCorrectTrue(attemptId);
            int finalScore = attempt.getScore() != null ? attempt.getScore().intValue() : 0;

            long timeSpent = 0;
            if (attempt.getStartTime() != null && attempt.getFinishTime() != null) {
                timeSpent = java.time.Duration.between(attempt.getStartTime(), attempt.getFinishTime()).getSeconds();
            }

            String mode = getAttemptMode(attempt);
            int position = calculatePosition(attempt.getQuiz().getId(), attempt.getUser().getId(), finalScore, timeSpent, mode);
            return new QuizResultDTO(
                    attemptId,
                    finalScore,
                    correctAnswers,
                    totalQuestions,
                    position,
                    timeSpent,
                    toLocalDateTime(attempt.getFinishTime()),
                    attempt.getCatStake(),
                    attempt.getCatStakeBonus(),
                    mode
            );
        }

        clearCurrentQuestionState(attempt);
        attempt.setCompleted(true);
        attempt.setFinishTime(Instant.now());
        attempt = attemptRepository.save(attempt);

        if (attempt.getSessionId() != null && !attempt.getSessionId().isEmpty()) {
            checkAndFinishMultiplayerSession(attempt.getSessionId());
        }

        List<UserAnswer> answers = userAnswerRepository.findByAttemptId(attemptId);

        List<AttemptQuestion> attemptQuestions = attemptQuestionRepository.findByAttemptIdOrderByQuestionOrder(attemptId);
        int totalQuestions = attemptQuestions.size();
        int correctAnswers = (int) userAnswerRepository.countByAttemptIdAndIsCorrectTrue(attemptId);
        int accuracyPercent = calculateAccuracyPercent(answers, totalQuestions);
        AttemptState st = attemptStates.get(attemptId);
        double rawScore = st != null
                ? st.score
                : (attempt.getScore() != null ? attempt.getScore().doubleValue() : 0.0);
        double rawBaseScore = st != null
                ? st.baseScore
                : getEffectiveBaseScore(attempt);
        long finalScoreLong = Math.round(rawScore);
        long finalBaseScoreLong = Math.round(rawBaseScore);

        attempt.setScore(finalScoreLong);
        attempt.setBaseScore(finalBaseScoreLong);
        attempt.setAccuracyPercent(accuracyPercent);
        attempt = attemptRepository.save(attempt);
        int finalScore = (int) finalScoreLong;
        int leaderboardScore = (int) finalBaseScoreLong;

        long timeSpent = 0;
        if (attempt.getStartTime() != null && attempt.getFinishTime() != null) {
            timeSpent = java.time.Duration.between(attempt.getStartTime(), attempt.getFinishTime()).getSeconds();
        }
        int attemptNumber = getCompletedAttemptNumber(attempt);

        String mode = getAttemptMode(attempt);
        leaderboardService.updateLeaderboard(
                attempt.getQuiz().getId(),
                attempt.getUser().getId(),
                attempt.getUser().getLogin(),
                leaderboardScore,
                timeSpent,
                accuracyPercent,
                attemptNumber,
                "solo"
        );
        if ("multiplayer".equals(mode)) {
            leaderboardService.updateLeaderboard(
                    attempt.getQuiz().getId(),
                    attempt.getUser().getId(),
                    attempt.getUser().getLogin(),
                    finalScore,
                    timeSpent,
                    accuracyPercent,
                    attemptNumber,
                    mode
            );
        }

        int position = "multiplayer".equals(mode)
                ? calculateSessionPosition(attempt.getQuiz().getId(), attempt.getSessionId(), attempt.getUser().getId())
                : calculatePosition(attempt.getQuiz().getId(), attempt.getUser().getId(), leaderboardScore, timeSpent, mode);

        if (attempt.getStartTime() != null && attempt.getFinishTime() != null) {
            long durationNanos = java.time.Duration.between(attempt.getStartTime(), attempt.getFinishTime()).toNanos();
            metricsService.getAttemptDurationTimer().record(durationNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
        }

        return new QuizResultDTO(
                attemptId,
                finalScore,
                correctAnswers,
                totalQuestions,
                position,
                timeSpent,
                toLocalDateTime(attempt.getFinishTime()),
                attempt.getCatStake(),
                attempt.getCatStakeBonus(),
                mode
        );
    }

    private void checkAndFinishMultiplayerSession(String sessionId) {
        try {
            List<UserQuizAttempt> attempts = attemptRepository.findBySessionId(sessionId);
            boolean allCompleted = attempts.stream().allMatch(UserQuizAttempt::isCompleted);
            
            if (allCompleted && attempts.size() >= 2) {
                org.example.model.MultiplayerSession session = multiplayerSessionRepository.findBySessionId(sessionId)
                    .orElse(null);
                
                if (session != null && !"FINISHED".equals(session.getStatus())) {
                    session.setStatus("FINISHED");
                    session.setFinishedAt(Instant.now());
                    multiplayerSessionRepository.save(session);
                }
            }
        } catch (Exception e) {
            log.debug("Не удалось проверить завершение мультиплеерной сессии {}", sessionId, e);
        }
    }

    public void setCatQuestionIndex(Long attemptId, Integer catQuestionIndex) {
        AttemptState st = getOrCreateAttemptState(attemptId);
        st.catQuestionIndex = catQuestionIndex;
        st.stakeForCurrentQuestion = null;
    }

    public double getCurrentScore(Long attemptId) {
        AttemptState st = attemptStates.get(attemptId);
        if (st != null) {
            return st.score;
        }
        UserQuizAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("Попытка не найдена"));
        return attempt.getScore() != null ? attempt.getScore().doubleValue() : 0.0;
    }

    public QuestionDTO submitStake(org.example.dto.request.attempt.SubmitStakeRequest request) {
        UserQuizAttempt attempt = attemptRepository.findById(request.attemptId())
                .orElseThrow(() -> new IllegalArgumentException("Попытка не найдена"));

        if (attempt.isCompleted()) {
            throw new IllegalStateException("Попытка уже завершена");
        }

        AttemptState st = getOrCreateAttemptState(request.attemptId());
        if (st.catQuestionIndex == null) {
            throw new IllegalStateException("В данной попытке нет вопроса «Кот в мешке»");
        }
        if (st.stakeForCurrentQuestion != null) {
            throw new IllegalStateException("Ставка уже сделана");
        }

        List<AttemptQuestion> aq = attemptQuestionRepository.findByAttemptIdOrderByQuestionOrder(request.attemptId());
        List<Long> answeredIds = userAnswerRepository.findByAttemptId(request.attemptId()).stream()
                .map(a -> a.getQuestion() != null ? a.getQuestion().getId() : null)
                .filter(id -> id != null)
                .collect(java.util.stream.Collectors.toList());
        int currentIndex = -1;
        for (int i = 0; i < aq.size(); i++) {
            Question q = aq.get(i).getQuestion();
            if (q != null && !answeredIds.contains(q.getId())) {
                currentIndex = i;
                break;
            }
        }
        if (currentIndex != st.catQuestionIndex) {
            throw new IllegalStateException("Ставку можно делать только на вопросе «Кот в мешке»");
        }

        int stake = request.stake() != null ? request.stake() : 0;
        double currentScore = getCurrentScore(request.attemptId());
        int maxStake = (int) Math.floor(currentScore);

        if (stake < 0) {
            throw new IllegalArgumentException("Ставка не может быть отрицательной");
        }
        if (currentScore <= 0 && stake != 0) {
            throw new IllegalArgumentException("При текущем счёте <= 0 допускается только ставка 0");
        }
        if (stake > maxStake) {
            throw new IllegalArgumentException("Ставка не может превышать текущий счёт (" + maxStake + ")");
        }

        st.stakeForCurrentQuestion = stake;
        attempt.setCatStake(stake);
        attempt.setCatStakeBonus(null);
        attemptRepository.save(attempt);

        return getNextQuestion(request.attemptId());
    }


    private Question getFirstQuestion(List<Question> questions) {
        List<Question> validQuestions = questions.stream()
                .filter(q -> q != null 
                        && q.getText() != null 
                        && !q.getText().trim().isEmpty())
                .collect(Collectors.toList());
        
        if (validQuestions.isEmpty()) {
            validQuestions = questions.stream()
                    .filter(q -> q != null && q.getText() != null && !q.getText().trim().isEmpty())
                    .collect(Collectors.toList());
        }
        
        if (validQuestions.isEmpty()) {
            throw new IllegalStateException("Нет вопросов с текстом для отображения");
        }
        
        return validQuestions.get(0);
    }

    private QuestionDTO toQuestionDTO(Long attemptId, Question question) {
        if (question == null) {
            throw new IllegalArgumentException("Question is null");
        }
        if (question.getText() == null || question.getText().trim().isEmpty()) {
        }

        java.util.Map<Long, BigDecimal> hundredToOneNominalsByOptionId = null;
        if (question.getType() == QuestionType.HUNDRED_TO_ONE) {
            hundredToOneNominalsByOptionId = ensureHundredToOneNominals(attemptId, question);
        }
            
        List<org.example.model.AnswerOption> options = answerOptionRepository.findByQuestionId(question.getId());
            
        if (options.isEmpty()) {
            throw new IllegalStateException("У вопроса ID " + question.getId() + " нет вариантов ответов");
        }

        if (question.getType() == QuestionType.HUNDRED_TO_ONE) {
            long shuffleSeed = attemptId * 1000003L + (question.getId() != null ? question.getId() : 0L);
            options = new ArrayList<>(options);
            Collections.shuffle(options, new Random(shuffleSeed));
        }

        List<AnswerOption> dtoOptions = new ArrayList<>();
        for (org.example.model.AnswerOption opt : options) {
            if (opt == null) {
                continue;
            }
            String optionText = opt.getText();
            if (optionText == null || optionText.trim().isEmpty()) {
                continue;
            }
            BigDecimal nominal = null;
            if (question.getType() == QuestionType.HUNDRED_TO_ONE) {
                nominal = hundredToOneNominalsByOptionId != null ? hundredToOneNominalsByOptionId.get(opt.getId()) : null;
            } else {
                nominal = opt.getNominal();
            }
            dtoOptions.add(new AnswerOption(opt.getId(), optionText, nominal));
        }
            
        if (dtoOptions.isEmpty()) {
            throw new IllegalStateException("У вопроса ID " + question.getId() + " нет валидных вариантов ответов");
        }

        Integer timeLimit = null;
        Quiz quiz = question.getQuiz();
        if (quiz != null && quiz.getTimePerQuestion() != null) {
            timeLimit = (int) quiz.getTimePerQuestion().getSeconds();
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
                quiz != null ? toLocalDateTime(quiz.getCreatedAt()) : null
        );
    }

    private AttemptState getOrCreateAttemptState(Long attemptId) {
        AttemptState st = attemptStates.get(attemptId);
        if (st == null) {
            UserQuizAttempt attempt = attemptRepository.findById(attemptId)
                    .orElseThrow(() -> new IllegalArgumentException("Попытка не найдена"));

            st = new AttemptState();
            st.attemptId = attemptId;
            st.userId = attempt.getUser() != null ? attempt.getUser().getId() : null;
            st.quizId = attempt.getQuiz() != null ? attempt.getQuiz().getId() : null;
            st.score = attempt.getScore() != null ? attempt.getScore().doubleValue() : 0.0;
            st.baseScore = getEffectiveBaseScore(attempt);
            st.stakeForCurrentQuestion = attempt.getCatStakeBonus() == null ? attempt.getCatStake() : null;
            st.hundredToOneNominalsByQuestionId = new ConcurrentHashMap<>();
            attemptStates.put(attemptId, st);
        }

        if (st.hundredToOneNominalsByQuestionId == null) {
            st.hundredToOneNominalsByQuestionId = new ConcurrentHashMap<>();
        }
        return st;
    }

    private java.util.Map<Long, BigDecimal> ensureHundredToOneNominals(Long attemptId, Question question) {
        AttemptState st = getOrCreateAttemptState(attemptId);

        java.util.Map<Long, BigDecimal> existing = st.hundredToOneNominalsByQuestionId.get(question.getId());
        if (existing != null) {
            return existing;
        }

        List<org.example.model.AnswerOption> options = answerOptionRepository.findByQuestionId(question.getId());
        if (options == null || options.isEmpty()) {
            throw new IllegalStateException("У вопроса ID " + question.getId() + " нет вариантов ответов");
        }

        java.util.List<Long> correctIds = options.stream()
                .filter(org.example.model.AnswerOption::isCorrect)
                .map(org.example.model.AnswerOption::getId)
                .toList();
        java.util.List<Long> incorrectIds = options.stream()
                .filter(o -> !o.isCorrect())
                .map(org.example.model.AnswerOption::getId)
                .toList();

        java.util.List<BigDecimal> correctPool = new java.util.ArrayList<>(
                java.util.List.of(
                        new BigDecimal("1"),
                        new BigDecimal("1.5"),
                        new BigDecimal("2"),
                        new BigDecimal("2.5"),
                        new BigDecimal("3")
                )
        );
        java.util.List<BigDecimal> incorrectPool = new java.util.ArrayList<>(
                java.util.List.of(
                        new BigDecimal("0"),
                        new BigDecimal("-1"),
                        new BigDecimal("-2")
                )
        );

        long seed = attemptId * 1000003L + (question.getId() != null ? question.getId() : 0L);
        Random rnd = new Random(seed);
        Collections.shuffle(correctPool, rnd);
        Collections.shuffle(incorrectPool, rnd);

        java.util.Map<Long, BigDecimal> mapping = new java.util.HashMap<>();

        for (int i = 0; i < correctIds.size(); i++) {
            BigDecimal nominal = correctPool.get(i % correctPool.size());
            mapping.put(correctIds.get(i), nominal);
        }
        for (int i = 0; i < incorrectIds.size(); i++) {
            BigDecimal nominal = incorrectPool.get(i % incorrectPool.size());
            mapping.put(incorrectIds.get(i), nominal);
        }

        st.hundredToOneNominalsByQuestionId.put(question.getId(), mapping);
        return mapping;
    }

    private int getQuestionIndexInAttempt(Long attemptId, Long questionId) {
        List<AttemptQuestion> attemptQuestions =
                attemptQuestionRepository.findByAttemptIdOrderByQuestionOrder(attemptId);
        for (int i = 0; i < attemptQuestions.size(); i++) {
            Question q = attemptQuestions.get(i).getQuestion();
            if (q != null && q.getId().equals(questionId)) {
                return i;
            }
        }
        return -1;
    }

    private Integer calculateScore(Question question, UserQuizAttempt attempt) {
        return 1;
    }

    private String getAttemptMode(UserQuizAttempt attempt) {
        return attempt != null && attempt.getSessionId() != null && !attempt.getSessionId().isBlank()
                ? "multiplayer"
                : "solo";
    }

    private int calculateAccuracyPercent(List<UserAnswer> answers, int totalQuestions) {
        if (totalQuestions <= 0) {
            return 0;
        }
        double accuracySum = answers.stream()
                .mapToDouble(answer -> {
                    if (answer.getAccuracyRatio() != null) {
                        return answer.getAccuracyRatio();
                    }
                    return Boolean.TRUE.equals(answer.getIsCorrect()) ? 1.0 : 0.0;
                })
                .sum();
        return (int) Math.round(accuracySum * 100.0 / totalQuestions);
    }

    private int getCompletedAttemptNumber(UserQuizAttempt attempt) {
        if (attempt == null || attempt.getUser() == null || attempt.getQuiz() == null || attempt.getId() == null) {
            return Integer.MAX_VALUE;
        }
        long attemptNumber = attemptRepository.countCompletedByUserIdAndQuizIdUpToAttemptId(
                attempt.getUser().getId(),
                attempt.getQuiz().getId(),
                attempt.getId()
        );
        return attemptNumber > 0 ? (int) attemptNumber : Integer.MAX_VALUE;
    }

    private long getEffectiveBaseScore(UserQuizAttempt attempt) {
        if (attempt == null) {
            return 0L;
        }
        if (attempt.getBaseScore() != null) {
            return attempt.getBaseScore();
        }
        long score = attempt.getScore() != null ? attempt.getScore() : 0L;
        return score - (attempt.getCatStakeBonus() != null ? attempt.getCatStakeBonus() : 0);
    }

    private int calculatePosition(Long quizId, Long currentUserId, int score, long timeSpent, String mode) {
        Pageable pageable = PageRequest.of(0, 10000);
        Page<UserQuizAttempt> allAttempts = "multiplayer".equalsIgnoreCase(mode)
                ? attemptRepository.findCompletedMultiplayerByQuizIdOrderByScoreDesc(quizId, pageable)
                : attemptRepository.findCompletedByQuizId(quizId, pageable);

        Map<Long, UserQuizAttempt> bestAttemptsByUser = new java.util.HashMap<>();
        
        for (UserQuizAttempt attempt : allAttempts.getContent()) {
            if (attempt.getUser() == null || attempt.getScore() == null) {
                continue;
            }
            
            Long userId = attempt.getUser().getId();
            UserQuizAttempt bestAttempt = bestAttemptsByUser.get(userId);
            
            if (bestAttempt == null) {
                bestAttemptsByUser.put(userId, attempt);
            } else {
                Long bestScore = "multiplayer".equalsIgnoreCase(mode) ? bestAttempt.getScore() : getEffectiveBaseScore(bestAttempt);
                Long currentScore = "multiplayer".equalsIgnoreCase(mode) ? attempt.getScore() : getEffectiveBaseScore(attempt);
                
                if (currentScore > bestScore) {
                    bestAttemptsByUser.put(userId, attempt);
                } else if (currentScore.equals(bestScore)) {
                    int bestAccuracy = bestAttempt.getAccuracyPercent() != null ? bestAttempt.getAccuracyPercent() : 0;
                    int currentAccuracy = attempt.getAccuracyPercent() != null ? attempt.getAccuracyPercent() : 0;
                    if (currentAccuracy > bestAccuracy) {
                        bestAttemptsByUser.put(userId, attempt);
                        continue;
                    }
                    if (currentAccuracy < bestAccuracy) {
                        continue;
                    }
                    int bestAttemptNumber = getCompletedAttemptNumber(bestAttempt);
                    int currentAttemptNumber = getCompletedAttemptNumber(attempt);
                    if (currentAttemptNumber < bestAttemptNumber) {
                        bestAttemptsByUser.put(userId, attempt);
                        continue;
                    }
                    if (currentAttemptNumber > bestAttemptNumber) {
                        continue;
                    }
                    long bestTime = 0;
                    long currentTime = 0;
                    
                    if (bestAttempt.getStartTime() != null && bestAttempt.getFinishTime() != null) {
                        bestTime = java.time.Duration.between(
                                bestAttempt.getStartTime(),
                                bestAttempt.getFinishTime()
                        ).getSeconds();
                    }
                    
                    if (attempt.getStartTime() != null && attempt.getFinishTime() != null) {
                        currentTime = java.time.Duration.between(
                                attempt.getStartTime(),
                                attempt.getFinishTime()
                        ).getSeconds();
                    }
                    
                    if (currentTime > 0 && (bestTime == 0 || currentTime < bestTime)) {
                        bestAttemptsByUser.put(userId, attempt);
                    }
                }
            }
        }

        List<UserQuizAttempt> sortedBestAttempts = new ArrayList<>(bestAttemptsByUser.values());
        sortedBestAttempts.sort((a, b) -> {
            Long scoreA = "multiplayer".equalsIgnoreCase(mode) ? (a.getScore() != null ? a.getScore() : 0L) : getEffectiveBaseScore(a);
            Long scoreB = "multiplayer".equalsIgnoreCase(mode) ? (b.getScore() != null ? b.getScore() : 0L) : getEffectiveBaseScore(b);
            
            int scoreCompare = scoreB.compareTo(scoreA);
            if (scoreCompare != 0) {
                return scoreCompare;
            }

            int accuracyA = a.getAccuracyPercent() != null ? a.getAccuracyPercent() : 0;
            int accuracyB = b.getAccuracyPercent() != null ? b.getAccuracyPercent() : 0;
            int accuracyCompare = Integer.compare(accuracyB, accuracyA);
            if (accuracyCompare != 0) {
                return accuracyCompare;
            }

            int attemptNumberA = getCompletedAttemptNumber(a);
            int attemptNumberB = getCompletedAttemptNumber(b);
            int attemptNumberCompare = Integer.compare(attemptNumberA, attemptNumberB);
            if (attemptNumberCompare != 0) {
                return attemptNumberCompare;
            }
            
            long timeA = 0;
            long timeB = 0;
            
            if (a.getStartTime() != null && a.getFinishTime() != null) {
                timeA = java.time.Duration.between(a.getStartTime(), a.getFinishTime()).getSeconds();
            }
            if (b.getStartTime() != null && b.getFinishTime() != null) {
                timeB = java.time.Duration.between(b.getStartTime(), b.getFinishTime()).getSeconds();
            }
            
            return Long.compare(timeA, timeB);
        });

        int position = 1;
        boolean found = false;
        for (UserQuizAttempt bestAttempt : sortedBestAttempts) {
            if (bestAttempt.getUser() != null && bestAttempt.getUser().getId().equals(currentUserId)) {
                found = true;
                break;
            }
            position++;
        }
        
        if (!found) {
            position = sortedBestAttempts.size() + 1;
        }

        return position;
    }

    private int calculateSessionPosition(Long quizId, String sessionId, Long currentUserId) {
        if (sessionId == null || sessionId.isBlank()) {
            return calculatePosition(quizId, currentUserId, 0, 0, "solo");
        }
        Pageable pageable = PageRequest.of(0, 10000);
        List<UserQuizAttempt> attempts = new ArrayList<>(
                attemptRepository.findCompletedByQuizIdAndSessionId(quizId, sessionId, pageable).getContent()
        );
        attempts.sort((a, b) -> {
            long scoreA = a.getScore() != null ? a.getScore() : 0L;
            long scoreB = b.getScore() != null ? b.getScore() : 0L;
            int scoreCompare = Long.compare(scoreB, scoreA);
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            int accuracyCompare = Integer.compare(
                    b.getAccuracyPercent() != null ? b.getAccuracyPercent() : 0,
                    a.getAccuracyPercent() != null ? a.getAccuracyPercent() : 0
            );
            if (accuracyCompare != 0) {
                return accuracyCompare;
            }
            return Long.compare(getTimeSpentSeconds(a), getTimeSpentSeconds(b));
        });
        for (int i = 0; i < attempts.size(); i++) {
            UserQuizAttempt attempt = attempts.get(i);
            if (attempt.getUser() != null && attempt.getUser().getId().equals(currentUserId)) {
                return i + 1;
            }
        }
        return attempts.size() + 1;
    }

    private long getTimeSpentSeconds(UserQuizAttempt attempt) {
        if (attempt == null || attempt.getStartTime() == null || attempt.getFinishTime() == null) {
            return 0L;
        }
        return java.time.Duration.between(attempt.getStartTime(), attempt.getFinishTime()).getSeconds();
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant != null
                ? LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
                : null;
    }

    private void selectQuestionsForAttempt(UserQuizAttempt attempt, Quiz quiz, List<Question> allQuestions) {
        List<Question> validQuestions = allQuestions.stream()
                .filter(q -> q != null 
                        && q.getText() != null 
                        && !q.getText().trim().isEmpty())
                .collect(Collectors.toList());
        
        if (validQuestions.isEmpty()) {
            validQuestions = allQuestions.stream()
                    .filter(q -> q != null && q.getText() != null && !q.getText().trim().isEmpty())
                    .collect(Collectors.toList());
        }
        
        if (validQuestions.isEmpty()) {
            throw new IllegalStateException("Нет доступных вопросов для выбора");
        }

        int questionNumber = quiz.getQuestionNumber() != null && quiz.getQuestionNumber() > 0 
                ? quiz.getQuestionNumber() 
                : validQuestions.size();
        
        questionNumber = Math.min(questionNumber, validQuestions.size());

        List<Question> selectedQuestions;
        
        if (quiz.isStatic()) {
            selectedQuestions = validQuestions.stream()
                    .sorted((q1, q2) -> Long.compare(q1.getId(), q2.getId()))
                    .limit(questionNumber)
                    .collect(Collectors.toList());
        } else {
            Collections.shuffle(validQuestions);
            selectedQuestions = validQuestions.stream()
                    .limit(questionNumber)
                    .collect(Collectors.toList());
        }

        for (int i = 0; i < selectedQuestions.size(); i++) {
            AttemptQuestion attemptQuestion = new AttemptQuestion();
            attemptQuestion.setAttempt(attempt);
            attemptQuestion.setQuestion(selectedQuestions.get(i));
            attemptQuestion.setQuestionOrder(i);
            attemptQuestionRepository.save(attemptQuestion);
        }
        
    }
}

package org.example.service;

import org.example.dto.kafka.QuizGenerationRequestMessage;
import org.example.dto.kafka.Stage;
import org.example.dto.ml.MlJobStateDTO;
import org.example.dto.ml.MlQuestionDTO;
import org.example.dto.ml.MlQuestionOptionDTO;
import org.example.dto.request.generation.QuestionGenerationRequest;
import org.example.dto.response.generation.QuestionGenerationResponse;
import org.example.kafka.KafkaQuizGenerationProperties;
import org.example.metrics.MetricsService;
import org.example.model.AnswerOption;
import org.example.model.GenerationSet;
import org.example.model.Question;
import org.example.model.QuestionType;
import org.example.model.Quiz;
import org.example.repository.AnswerOptionRepository;
import org.example.repository.GenerationSetRepository;
import org.example.repository.QuestionRepository;
import org.example.repository.QuizRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class QuestionGenerationService {
    private static final Logger logger = LoggerFactory.getLogger(QuestionGenerationService.class);
    private static final String STATUS_GENERATING = "GENERATING";
    private static final String STATUS_WAITING_FOR_ML = "WAITING_FOR_ML";
    private static final String STATUS_RETRYING = "RETRYING";
    private static final String STATUS_READY = "READY";
    private static final String STATUS_FAILED = "FAILED";
    private static final String REASON_UNETHICAL = "UNETHICAL_INPUT";
    private static final String REASON_GENERATION_FAILED = "GENERATION_FAILED";
    private static final String REASON_ML_UNAVAILABLE = "ML_UNAVAILABLE";
    private static final String REASON_RETRIES_EXHAUSTED = "RETRIES_EXHAUSTED";
    private static final String MESSAGE_UNETHICAL = "Промпт или материал не прошёл проверку безопасности. Квиз по этим данным создан не будет.";
    private static final String MESSAGE_GENERATION_FAILED = "Не удалось сгенерировать вопросы для этого квиза. Попробуйте изменить промпт или материал.";
    private static final String MESSAGE_ML_UNAVAILABLE = "ML-сервис сейчас недоступен. Генерация этого квиза остановлена, попробуйте позже.";
    private static final String MESSAGE_RETRIES_EXHAUSTED = "ML-сервис не успел завершить генерацию. Квиз не будет создан автоматически, попробуйте запустить создание позже.";
    private static final int DEFAULT_GENERATION_QUESTION_COUNT = 10;
    private static final int MAX_ML_GENERATION_QUESTION_COUNT = 50;

    private final GenerationSetRepository generationSetRepository;
    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final AnswerOptionRepository answerOptionRepository;
    private final FastApiClient fastApiClient;
    private final FileStorageService fileStorageService;
    private final TextExtractorService textExtractorService;
    private final KafkaTemplate<String, QuizGenerationRequestMessage> requestKafkaTemplate;
    private final String requestTopic;
    private final QuizCacheEvictService quizCacheEvictService;
    private final MetricsService metricsService;

    @Autowired
    public QuestionGenerationService(
            GenerationSetRepository generationSetRepository,
            QuizRepository quizRepository,
            QuestionRepository questionRepository,
            AnswerOptionRepository answerOptionRepository,
            FastApiClient fastApiClient,
            FileStorageService fileStorageService,
            TextExtractorService textExtractorService,
            KafkaTemplate<String, QuizGenerationRequestMessage> quizGenerationRequestKafkaTemplate,
            KafkaQuizGenerationProperties kafkaQuizGenerationProperties,
            QuizCacheEvictService quizCacheEvictService,
            MetricsService metricsService) {
        this.generationSetRepository = generationSetRepository;
        this.quizRepository = quizRepository;
        this.questionRepository = questionRepository;
        this.answerOptionRepository = answerOptionRepository;
        this.fastApiClient = fastApiClient;
        this.fileStorageService = fileStorageService;
        this.textExtractorService = textExtractorService;
        this.requestKafkaTemplate = quizGenerationRequestKafkaTemplate;
        this.requestTopic = kafkaQuizGenerationProperties.getRequestTopic();
        this.quizCacheEvictService = quizCacheEvictService;
        this.metricsService = metricsService;
    }

    public QuestionGenerationResponse generateQuizQuestionsKafka(QuestionGenerationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is null");
        }

        Quiz quiz = quizRepository.findById(request.quizId())
                .orElseThrow(() -> new IllegalArgumentException("Квиз не найден: " + request.quizId()));

        GenerationSet questionSet = createGenerationSet(quiz, request.prompt());
        String correlationId = UUID.randomUUID().toString();
        String preferredQuestionType = request.preferredQuestionType() != null
                ? request.preferredQuestionType().name()
                : null;

        List<String> materialUrls = materialFileUrls(request);
        if (materialUrls.isEmpty() && quiz.isHasMaterial()
                && quiz.getMaterialUrl() != null && !quiz.getMaterialUrl().isBlank()) {
            materialUrls = List.of(quiz.getMaterialUrl());
        }

        logger.info(
                "Queueing quiz generation for questionSetId={}, quizId={}, materialUrls={}",
                questionSet.getId(),
                request.quizId(),
                materialUrls
        );

        QuizGenerationRequestMessage kafkaRequest = new QuizGenerationRequestMessage(
                correlationId,
                questionSet.getId(),
                request.quizId(),
                request.prompt(),
                request.questionCount(),
                preferredQuestionType,
                Stage.START,
                null,
                materialUrls
        );

        sendKafkaRequestAfterCommit(questionSet, kafkaRequest);

        return new QuestionGenerationResponse(questionSet.getId(), questionSet.getStatus(), 0, 0, 0, 0);
    }

    private void sendKafkaRequestAfterCommit(GenerationSet questionSet, QuizGenerationRequestMessage kafkaRequest) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendKafkaRequest(questionSet, kafkaRequest);
                }
            });
            return;
        }

        sendKafkaRequest(questionSet, kafkaRequest);
    }

    private void sendKafkaRequest(GenerationSet questionSet, QuizGenerationRequestMessage kafkaRequest) {
        try {
            requestKafkaTemplate.send(requestTopic, kafkaRequest.correlationId(), kafkaRequest);
        } catch (Exception e) {
            markGenerationSetFailed(questionSet, REASON_ML_UNAVAILABLE, MESSAGE_ML_UNAVAILABLE);
            throw new RuntimeException("Kafka send failed", e);
        }
    }

    public void processKafkaQuizGenerationRequest(QuizGenerationRequestMessage kafkaRequest) {
        if (kafkaRequest == null) {
            throw new IllegalArgumentException("kafkaRequest is null");
        }

        GenerationSet generationSet = null;
        long generationStart = System.nanoTime();
        try {
            if (kafkaRequest.questionSetId() == null) {
                throw new IllegalArgumentException("questionSetId is required");
            }

            generationSet = generationSetRepository.findById(kafkaRequest.questionSetId())
                    .orElseThrow(() -> new IllegalArgumentException("Набор генерации не найден"));

            if (STATUS_READY.equals(generationSet.getStatus())) {
                return;
            }

            Stage stage = kafkaRequest.stage() != null ? kafkaRequest.stage() : Stage.START;
            if (stage == Stage.POLL) {
                pollMlGenerationJob(generationSet, kafkaRequest);
            } else {
                startMlGenerationJob(generationSet, kafkaRequest);
            }

            metricsService.getGenerationDurationTimer().record(System.nanoTime() - generationStart, TimeUnit.NANOSECONDS);
        } catch (GenerationJobNotReadyException e) {
            metricsService.getGenerationDurationTimer().record(System.nanoTime() - generationStart, TimeUnit.NANOSECONDS);
            throw e;
        } catch (GenerationRetryableException e) {
            if (generationSet != null && !STATUS_READY.equals(generationSet.getStatus())) {
                generationSet.setStatus(STATUS_RETRYING);
                generationSetRepository.save(generationSet);
            }
            metricsService.getGenerationDurationTimer().record(System.nanoTime() - generationStart, TimeUnit.NANOSECONDS);
            throw e;
        } catch (UnethicalPromptException e) {
            markGenerationSetFailed(generationSet, REASON_UNETHICAL, MESSAGE_UNETHICAL);
            metricsService.recordGenerationUnethical();
            metricsService.getGenerationDurationTimer().record(System.nanoTime() - generationStart, TimeUnit.NANOSECONDS);
        } catch (GenerationNonRetryableException | IllegalArgumentException e) {
            markGenerationSetFailed(generationSet, REASON_GENERATION_FAILED, userFriendlyFailureMessage(e, MESSAGE_GENERATION_FAILED));
            metricsService.recordGenerationFailed();
            metricsService.getGenerationDurationTimer().record(System.nanoTime() - generationStart, TimeUnit.NANOSECONDS);
        } catch (Exception e) {
            markGenerationSetFailed(generationSet, REASON_ML_UNAVAILABLE, userFriendlyFailureMessage(e, MESSAGE_ML_UNAVAILABLE));
            metricsService.recordGenerationFailed();
            metricsService.getGenerationDurationTimer().record(System.nanoTime() - generationStart, TimeUnit.NANOSECONDS);
        }
    }

    private String userFriendlyFailureMessage(Exception e, String fallback) {
        if (e == null || e.getMessage() == null || e.getMessage().isBlank()) {
            return fallback;
        }
        String message = e.getMessage();
        if (message.toLowerCase().contains("unethical")) {
            return MESSAGE_UNETHICAL;
        }
        return fallback;
    }

    private void startMlGenerationJob(GenerationSet generationSet, QuizGenerationRequestMessage kafkaRequest)
            throws IOException, InterruptedException, UnethicalPromptException {
        if (generationSet.getMlJobId() != null && !generationSet.getMlJobId().isBlank()) {
            sendPollMessage(kafkaRequest, generationSet.getMlJobId());
            return;
        }

        if (kafkaRequest.prompt() != null && !kafkaRequest.prompt().trim().isEmpty()
                && fastApiClient.checkPromptEthics(kafkaRequest.prompt())) {
            metricsService.recordGenerationUnethical();
            markGenerationSetFailed(generationSet, REASON_UNETHICAL, MESSAGE_UNETHICAL);
            return;
        }

        QuestionType preferred = parsePreferredQuestionType(kafkaRequest.preferredQuestionType());
        int questionCount = normalizeGenerationQuestionCount(kafkaRequest.questionCount());
        List<Path> materialFiles = resolveMaterialFiles(kafkaRequest.materialFileUrls());
        String generationPrompt = buildPromptWithExtractedMaterials(kafkaRequest.prompt(), materialFiles);
        logger.info(
                "Starting ML generation for questionSetId={}, quizId={}, materialFiles={}, extractedPromptLength={}",
                generationSet.getId(),
                kafkaRequest.quizId(),
                materialFiles,
                generationPrompt.length()
        );
        MlJobStateDTO job = fastApiClient.startGenerationJob(
                generationPrompt,
                questionCount,
                preferred
        );

        generationSet.setStatus(STATUS_WAITING_FOR_ML);
        generationSet.setMlJobId(job.id());
        resetCounters(generationSet);
        generationSetRepository.save(generationSet);

        if (fastApiClient.isJobFinished(job)) {
            finishMlGenerationJob(generationSet, kafkaRequest, job);
            return;
        }
        if (fastApiClient.isJobFailed(job)) {
            fastApiClient.extractFinishedQuestions(job);
        }

        sendPollMessage(kafkaRequest, job.id());
    }

    private void pollMlGenerationJob(GenerationSet generationSet, QuizGenerationRequestMessage kafkaRequest)
            throws IOException, InterruptedException {
        String mlJobId = kafkaRequest.mlJobId() != null && !kafkaRequest.mlJobId().isBlank()
                ? kafkaRequest.mlJobId()
                : generationSet.getMlJobId();

        if (mlJobId == null || mlJobId.isBlank()) {
            throw new GenerationNonRetryableException("Не найден идентификатор задания ML для набора генерации " + generationSet.getId());
        }

        MlJobStateDTO job = fastApiClient.getGenerationJob(mlJobId);
        if (fastApiClient.isJobFailed(job)) {
            fastApiClient.extractFinishedQuestions(job);
        }
        if (!fastApiClient.isJobFinished(job)) {
            generationSet.setStatus(STATUS_WAITING_FOR_ML);
            generationSet.setMlJobId(mlJobId);
            generationSetRepository.save(generationSet);
            throw new GenerationJobNotReadyException("Задание ML ещё не завершено: " + mlJobId);
        }

        finishMlGenerationJob(generationSet, kafkaRequest, job);
    }

    private void finishMlGenerationJob(
            GenerationSet generationSet,
            QuizGenerationRequestMessage kafkaRequest,
            MlJobStateDTO job
    ) {
        QuestionGenerationRequest internalRequest = new QuestionGenerationRequest(
                kafkaRequest.quizId(),
                kafkaRequest.prompt(),
                null,
                null,
                kafkaRequest.questionCount(),
                parsePreferredQuestionType(kafkaRequest.preferredQuestionType())
        );
        saveMlQuestions(generationSet, internalRequest, fastApiClient.extractFinishedQuestions(job));
        metricsService.recordGenerationSuccess();
    }

    private void sendPollMessage(QuizGenerationRequestMessage originalRequest, String mlJobId) {
        QuizGenerationRequestMessage pollRequest = new QuizGenerationRequestMessage(
                originalRequest.correlationId(),
                originalRequest.questionSetId(),
                originalRequest.quizId(),
                originalRequest.prompt(),
                originalRequest.questionCount(),
                originalRequest.preferredQuestionType(),
                Stage.POLL,
                mlJobId,
                originalRequest.materialFileUrls()
        );
        try {
            requestKafkaTemplate.send(requestTopic, pollRequest.correlationId(), pollRequest);
        } catch (Exception e) {
            throw new GenerationRetryableException("Не удалось поставить сообщение проверки ML в очередь", e);
        }
    }

    @Transactional
    public QuestionGenerationResponse generateExistingQuestionSet(Long questionSetId, QuestionGenerationRequest request) {
        GenerationSet existingSet = generationSetRepository.findById(questionSetId)
                .orElseThrow(() -> new IllegalArgumentException("Набор генерации не найден"));

        Quiz quiz = quizRepository.findById(request.quizId())
                .orElseThrow(() -> new IllegalArgumentException("Квиз не найден: " + request.quizId()));

        existingSet.setQuiz(quiz);
        existingSet.setPrompt(request.prompt());
        existingSet.setStatus(STATUS_GENERATING);
        existingSet.setMlJobId(null);
        existingSet.setFailureReason(null);
        existingSet.setFailureMessage(null);
        resetCounters(existingSet);
        generationSetRepository.save(existingSet);

        return generateQuizQuestionsUsingSet(existingSet, request);
    }

    private QuestionGenerationResponse generateQuizQuestionsUsingSet(GenerationSet questionSet, QuestionGenerationRequest request) {
        int questionCount = normalizeGenerationQuestionCount(request.questionCount());
        String prompt = request.prompt() != null ? request.prompt() : "Учебный материал";
        List<MlQuestionDTO> mlQuestions;

        try {
            mlQuestions = fastApiClient.generateQuestionsStructured(prompt, questionCount, request.preferredQuestionType());
        } catch (GenerationRetryableException | GenerationNonRetryableException | UnethicalPromptException e) {
            throw e;
        } catch (IOException e) {
            throw new GenerationRetryableException("ML-сервис недоступен: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GenerationRetryableException("Генерация была прервана во время запроса к ML-сервису", e);
        }

        return saveMlQuestions(questionSet, request, mlQuestions);
    }

    private QuestionGenerationResponse saveMlQuestions(
            GenerationSet questionSet,
            QuestionGenerationRequest request,
            List<MlQuestionDTO> mlQuestions
    ) {
        Quiz quiz = questionSet.getQuiz();
        int questionCount = normalizeGenerationQuestionCount(request.questionCount());
        List<Question> generatedQuestions = new ArrayList<>();

        questionRepository.deleteByGenerationSetId(questionSet.getId());

        if (mlQuestions == null || mlQuestions.isEmpty()) {
            throw new GenerationNonRetryableException("ML-сервис не вернул вопросы");
        }

        for (int i = 0; i < mlQuestions.size() && generatedQuestions.size() < questionCount; i++) {
            MlQuestionDTO mq = mlQuestions.get(i);
            if (mq == null || mq.question() == null || mq.question().trim().isEmpty()) {
                metricsService.recordValidationFailed();
                continue;
            }

            QuestionType type = mapMlType(mq.type(), request.preferredQuestionType());
            List<MlQuestionOptionDTO> options = mq.options() != null ? mq.options() : List.of();
            List<String> correctIds = mq.correct_answers() != null ? mq.correct_answers() : List.of();
            String explanation = mq.explanation() != null ? mq.explanation().trim() : "";

            if (!isValidMlQuestion(type, options, correctIds) || hasEncodingDamage(mq.question(), explanation, options)) {
                metricsService.recordValidationFailed();
                continue;
            }

            Question question = new Question();
            question.setQuiz(quiz);
            question.setText(mq.question().trim());
            question.setType(type);
            question.setGenerationSetId(questionSet.getId());

            question.setExplanation(explanation.isEmpty() ? "Объяснение отсутствует" : explanation);

            question = questionRepository.save(question);
            saveAnswerOptions(question, options, correctIds);
            generatedQuestions.add(question);
        }

        if (generatedQuestions.isEmpty()) {
            throw new GenerationNonRetryableException("Не удалось сохранить ни одного валидного вопроса из ответа ML-сервиса");
        }

        questionSet.setGeneratedCount(generatedQuestions.size());
        questionSet.setValidCount(generatedQuestions.size());
        questionSet.setFinalCount(generatedQuestions.size());
        questionSet.setStatus(STATUS_READY);
        questionSet.setFailureReason(null);
        questionSet.setFailureMessage(null);
        generationSetRepository.save(questionSet);

        metricsService.recordQuestionsGenerated(generatedQuestions.size());
        quizCacheEvictService.evictQuizCache(quiz.getId());

        return new QuestionGenerationResponse(
                questionSet.getId(),
                questionSet.getStatus(),
                generatedQuestions.size(),
                generatedQuestions.size(),
                0,
                generatedQuestions.size()
        );
    }

    private boolean isValidMlQuestion(QuestionType type, List<MlQuestionOptionDTO> options, List<String> correctIds) {
        long optionCount = options.stream()
                .filter(o -> o != null && o.text() != null && !o.text().trim().isEmpty())
                .count();
        long correctCount = options.stream()
                .filter(o -> o != null && o.id() != null && correctIds.stream()
                        .anyMatch(id -> id != null && id.equalsIgnoreCase(o.id())))
                .count();
        long wrongCount = optionCount - correctCount;

        if (type == QuestionType.HUNDRED_TO_ONE) {
            return optionCount == 8 && correctCount == 5 && wrongCount == 3;
        }
        if (type == QuestionType.SINGLE_CHOICE) {
            return optionCount >= 3 && optionCount <= 6 && correctCount == 1;
        }
        if (type == QuestionType.MULTIPLE_CHOICE) {
            return optionCount >= 4 && correctCount >= 2;
        }
        if (type == QuestionType.TRUE_FALSE) {
            return optionCount == 2 && correctCount == 1;
        }
        return false;
    }

    private boolean hasEncodingDamage(String question, String explanation, List<MlQuestionOptionDTO> options) {
        if (containsReplacementCharacter(question) || containsReplacementCharacter(explanation)) {
            return true;
        }
        if (options == null) {
            return false;
        }
        return options.stream()
                .anyMatch(option -> option != null && containsReplacementCharacter(option.text()));
    }

    private boolean containsReplacementCharacter(String value) {
        return value != null && value.indexOf('\uFFFD') >= 0;
    }

    private void saveAnswerOptions(Question question, List<MlQuestionOptionDTO> options, List<String> correctIds) {
        for (MlQuestionOptionDTO opt : options) {
            if (opt == null || opt.text() == null || opt.text().trim().isEmpty()) {
                continue;
            }
            AnswerOption ao = new AnswerOption();
            ao.setQuestion(question);
            ao.setText(opt.text().trim());
            boolean isCorrect = false;
            if (opt.id() != null) {
                for (String cid : correctIds) {
                    if (cid != null && cid.equalsIgnoreCase(opt.id().trim())) {
                        isCorrect = true;
                        break;
                    }
                }
            }
            ao.setCorrect(isCorrect);
            answerOptionRepository.save(ao);
        }
    }

    private void markGenerationSetFailed(GenerationSet generationSet) {
        markGenerationSetFailed(generationSet, REASON_GENERATION_FAILED, MESSAGE_GENERATION_FAILED);
    }

    private void markGenerationSetFailed(GenerationSet generationSet, String reason, String message) {
        if (generationSet == null || STATUS_READY.equals(generationSet.getStatus())) {
            return;
        }
        generationSet.setStatus(STATUS_FAILED);
        generationSet.setFailureReason(reason);
        generationSet.setFailureMessage(message);
        generationSetRepository.save(generationSet);
        if (generationSet.getQuiz() != null && generationSet.getQuiz().getId() != null) {
            quizCacheEvictService.evictQuizCache(generationSet.getQuiz().getId());
        }
    }

    public void markKafkaGenerationRetriesExhausted(QuizGenerationRequestMessage kafkaRequest) {
        if (kafkaRequest == null || kafkaRequest.questionSetId() == null) {
            return;
        }

        generationSetRepository.findById(kafkaRequest.questionSetId())
                .ifPresent(generationSet -> markGenerationSetFailed(
                        generationSet,
                        REASON_RETRIES_EXHAUSTED,
                        MESSAGE_RETRIES_EXHAUSTED
                ));
    }

    private GenerationSet createGenerationSet(Quiz quiz, String prompt) {
        GenerationSet questionSet = new GenerationSet();
        questionSet.setQuiz(quiz);
        questionSet.setPrompt(prompt);
        questionSet.setStatus(STATUS_GENERATING);
        questionSet.setFailureReason(null);
        questionSet.setFailureMessage(null);
        questionSet.setCreatedAt(Instant.now());
        resetCounters(questionSet);
        return generationSetRepository.save(questionSet);
    }

    private List<String> materialFileUrls(QuestionGenerationRequest request) {
        if (request.materials() == null || request.materials().isEmpty()) {
            return List.of();
        }
        return request.materials().stream()
                .filter(material -> material != null && material.fileUrl() != null && !material.fileUrl().isBlank())
                .map(org.example.dto.common.QuizMaterial::fileUrl)
                .collect(Collectors.toList());
    }

    private List<Path> resolveMaterialFiles(List<String> materialFileUrls) {
        if (materialFileUrls == null || materialFileUrls.isEmpty()) {
            return List.of();
        }
        return materialFileUrls.stream()
                .map(fileStorageService::resolveMaterialUrl)
                .collect(Collectors.toList());
    }

    private String buildPromptWithExtractedMaterials(String prompt, List<Path> materialFiles) {
        String normalizedPrompt = prompt != null ? prompt.trim() : "";
        if (materialFiles == null || materialFiles.isEmpty()) {
            return normalizedPrompt;
        }

        StringBuilder builder = new StringBuilder();
        if (!normalizedPrompt.isBlank() && hasMeaningfulText(normalizedPrompt)) {
            builder.append("Уточнение пользователя:\n")
                    .append(normalizedPrompt)
                    .append("\n\n");
        }
        builder.append("Учебный материал для генерации квиза:\n");

        boolean hasExtractedMaterial = false;
        for (Path file : materialFiles) {
            String extracted = textExtractorService.extractText(file);
            if (extracted.isBlank()) {
                continue;
            }
            hasExtractedMaterial = true;
            builder.append("\n--- Файл: ")
                    .append(file.getFileName())
                    .append(" ---\n")
                    .append(extracted)
                    .append("\n");
        }

        String result = builder.toString().trim();
        if (!hasExtractedMaterial || result.isBlank()) {
            throw new GenerationNonRetryableException("Не удалось извлечь текст из материалов квиза");
        }
        return result;
    }

    private boolean hasMeaningfulText(String value) {
        return value != null && value.codePoints().anyMatch(Character::isLetterOrDigit);
    }

    private void resetCounters(GenerationSet generationSet) {
        generationSet.setGeneratedCount(0);
        generationSet.setValidCount(0);
        generationSet.setDuplicateCount(0);
        generationSet.setFinalCount(0);
    }

    private int normalizeGenerationQuestionCount(Integer requestedCount) {
        if (requestedCount == null || requestedCount < 1) {
            return DEFAULT_GENERATION_QUESTION_COUNT;
        }
        return Math.min(requestedCount, MAX_ML_GENERATION_QUESTION_COUNT);
    }

    private QuestionType parsePreferredQuestionType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return null;
        }
        try {
            return QuestionType.valueOf(rawType.trim().toUpperCase());
        } catch (Exception ignored) {
            return null;
        }
    }

    private QuestionType mapMlType(String mlType, QuestionType preferredFallback) {
        String t = mlType != null ? mlType.trim().toLowerCase() : "";
        return switch (t) {
            case "multiple_choice" -> QuestionType.MULTIPLE_CHOICE;
            case "true_false" -> QuestionType.TRUE_FALSE;
            case "100k1", "q100k1", "hundred_to_one" -> QuestionType.HUNDRED_TO_ONE;
            case "single_choice" -> QuestionType.SINGLE_CHOICE;
            default -> preferredFallback != null ? preferredFallback : QuestionType.SINGLE_CHOICE;
        };
    }

    @Transactional
    public QuestionGenerationResponse generateQuizQuestions(QuestionGenerationRequest request) {
        Quiz quiz = quizRepository.findById(request.quizId())
                .orElseThrow(() -> new IllegalArgumentException("Квиз не найден: " + request.quizId()));

        GenerationSet questionSet = createGenerationSet(quiz, request.prompt());
        return generateQuizQuestionsUsingSet(questionSet, request);
    }

    @Transactional(readOnly = true)
    public org.example.dto.response.generation.ValidationResponse validateGeneratedQuestions(Long questionSetId) {
        GenerationSet questionSet = generationSetRepository.findById(questionSetId)
                .orElseThrow(() -> new IllegalArgumentException("Набор генерации не найден"));

        Long quizId = questionSet.getQuiz().getId();
        List<Question> generatedQuestions = questionRepository.findByQuizId(quizId).stream()
                .filter(q -> q.getGenerationSetId() != null && questionSetId.equals(q.getGenerationSetId()))
                .collect(Collectors.toList());

        return new org.example.dto.response.generation.ValidationResponse(
                questionSetId,
                generatedQuestions.size(),
                generatedQuestions.size(),
                new ArrayList<>()
        );
    }

    @Transactional(readOnly = true)
    public org.example.dto.response.generation.DeduplicationResponse removeDuplicateQuestions(Long questionSetId) {
        GenerationSet questionSet = generationSetRepository.findById(questionSetId)
                .orElseThrow(() -> new IllegalArgumentException("Набор генерации не найден"));

        Long quizId = questionSet.getQuiz().getId();
        List<Question> validQuestions = questionRepository.findByQuizId(quizId).stream()
                .filter(q -> q.getGenerationSetId() != null && questionSetId.equals(q.getGenerationSetId()))
                .collect(Collectors.toList());

        return new org.example.dto.response.generation.DeduplicationResponse(
                questionSetId,
                validQuestions.size(),
                validQuestions.size(),
                new ArrayList<>()
        );
    }

    @Transactional(readOnly = true)
    public org.example.dto.response.generation.GeneratedQuestionsDTO getGeneratedQuestions(Long questionSetId) {
        GenerationSet questionSet = generationSetRepository.findById(questionSetId)
                .orElseThrow(() -> new IllegalArgumentException("Набор генерации не найден"));

        Long quizId = questionSet.getQuiz().getId();
        List<Question> generatedQuestions = questionRepository.findByQuizId(quizId).stream()
                .filter(q -> q.getGenerationSetId() != null && questionSetId.equals(q.getGenerationSetId()))
                .collect(Collectors.toList());

        List<org.example.dto.response.quiz.QuestionDTO> questionDTOs = generatedQuestions.stream()
                .map(q -> {
                    List<AnswerOption> options = answerOptionRepository.findByQuestionId(q.getId());
                    List<org.example.dto.common.AnswerOption> dtoOptions = options.stream()
                            .map(opt -> new org.example.dto.common.AnswerOption(opt.getId(), opt.getText(), opt.getNominal()))
                            .collect(Collectors.toList());

                    return new org.example.dto.response.quiz.QuestionDTO(
                            q.getId(),
                            q.getText(),
                            dtoOptions,
                            q.getType(),
                            q.getQuiz().getTimePerQuestion() != null
                                    ? (int) q.getQuiz().getTimePerQuestion().getSeconds()
                                    : null,
                            null,
                            q.getExplanation(),
                            null,
                            null,
                            0,
                            toLocalDateTime(q.getQuiz().getCreatedAt())
                    );
                })
                .collect(Collectors.toList());

        org.example.dto.common.GenerationMetadata metadata = new org.example.dto.common.GenerationMetadata(
                toLocalDateTime(questionSet.getCreatedAt()),
                "1.0",
                String.valueOf(questionSet.getPrompt() != null ? questionSet.getPrompt().hashCode() : 0)
        );

        return new org.example.dto.response.generation.GeneratedQuestionsDTO(questionSetId, questionDTOs, metadata);
    }

    private java.time.LocalDateTime toLocalDateTime(Instant instant) {
        return instant != null
                ? java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())
                : null;
    }
}

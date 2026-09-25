package org.example.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.example.repository.UserQuizAttemptRepository;
import org.springframework.stereotype.Service;

@Service
public class MetricsService {

    private final Counter answersCorrect;
    private final Counter answersIncorrect;
    private final Counter generationSuccess;
    private final Counter generationFailed;
    private final Counter generationUnethical;
    private final Counter validationFailed;
    private final Counter kafkaDltMessages;

    private final Timer attemptDuration;
    private final Timer generationDuration;
    private final Timer fastApiEthicsTimer;
    private final Timer fastApiGenerateTimer;
    private final Timer kafkaProcessingTimer;

    private final DistributionSummary questionsGenerated;

    public MetricsService(MeterRegistry registry, UserQuizAttemptRepository attemptRepository) {
        Gauge.builder("quizarena_active_attempts", attemptRepository, UserQuizAttemptRepository::countByIsCompletedFalse)
                .register(registry);

        answersCorrect = registry.counter("quizarena_answers_total", "result", "correct");
        answersIncorrect = registry.counter("quizarena_answers_total", "result", "incorrect");
        generationSuccess = registry.counter("quizarena_generation_total", "status", "success");
        generationFailed = registry.counter("quizarena_generation_total", "status", "failed");
        generationUnethical = registry.counter("quizarena_generation_total", "status", "unethical");
        validationFailed = registry.counter("quizarena_validation_failed_total");
        kafkaDltMessages = registry.counter("quizarena_kafka_dlt_total");

        attemptDuration = registry.timer("quizarena_attempt_duration_seconds");
        generationDuration = registry.timer("quizarena_generation_duration_seconds");
        fastApiEthicsTimer = registry.timer("quizarena_fastapi_seconds", "endpoint", "ethics");
        fastApiGenerateTimer = registry.timer("quizarena_fastapi_seconds", "endpoint", "generate");
        kafkaProcessingTimer = registry.timer("quizarena_kafka_processing_seconds");

        questionsGenerated = DistributionSummary.builder("quizarena_questions_generated")
                .register(registry);
    }

    public void recordCorrectAnswer() { answersCorrect.increment(); }
    public void recordIncorrectAnswer() { answersIncorrect.increment(); }

    public void recordGenerationSuccess() { generationSuccess.increment(); }
    public void recordGenerationFailed() { generationFailed.increment(); }
    public void recordGenerationUnethical() { generationUnethical.increment(); }

    public void recordValidationFailed() { validationFailed.increment(); }
    public void recordKafkaDltMessage() { kafkaDltMessages.increment(); }

    public void recordQuestionsGenerated(int count) { questionsGenerated.record(count); }

    public Timer getAttemptDurationTimer() { return attemptDuration; }
    public Timer getGenerationDurationTimer() { return generationDuration; }
    public Timer getFastApiEthicsTimer() { return fastApiEthicsTimer; }
    public Timer getFastApiGenerateTimer() { return fastApiGenerateTimer; }
    public Timer getKafkaProcessingTimer() { return kafkaProcessingTimer; }
}

package org.example.kafka;

import org.example.dto.kafka.QuizGenerationRequestMessage;
import org.example.metrics.MetricsService;
import org.example.service.GenerationJobNotReadyException;
import org.example.service.GenerationNonRetryableException;
import org.example.service.GenerationRetryableException;
import org.example.service.QuestionGenerationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class QuizGenerationRequestConsumer {
    private static final Logger logger = LoggerFactory.getLogger(QuizGenerationRequestConsumer.class);

    private final QuestionGenerationService questionGenerationService;
    private final MetricsService metricsService;

    public QuizGenerationRequestConsumer(
            QuestionGenerationService questionGenerationService,
            MetricsService metricsService
    ) {
        this.questionGenerationService = questionGenerationService;
        this.metricsService = metricsService;
    }

    @KafkaListener(
            topics = "${quizarena.kafka.quiz-generation.request-topic}",
            groupId = "${quizarena.kafka.quiz-generation.worker-group-id}",
            containerFactory = "quizGenerationRequestKafkaListenerContainerFactory"
    )
    @RetryableTopic(
            attempts = "${quizarena.kafka.quiz-generation.retry-attempts}",
            backoff = @Backoff(delayExpression = "${quizarena.kafka.quiz-generation.retry-backoff-ms}"),
            autoCreateTopics = "true",
            numPartitions = "${quizarena.kafka.quiz-generation.request-partitions}",
            dltTopicSuffix = "-dlt",
            exclude = {
                    IllegalArgumentException.class,
                    GenerationNonRetryableException.class
            }
    )
    public void onRequest(QuizGenerationRequestMessage request) {
        if (request == null) {
            return;
        }

        long start = System.nanoTime();
        try {
            questionGenerationService.processKafkaQuizGenerationRequest(request);
            metricsService.getKafkaProcessingTimer().record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        } catch (GenerationJobNotReadyException e) {
            metricsService.getKafkaProcessingTimer().record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            logger.info(
                    "ML job пока не готов для questionSetId={}. Kafka проверит результат позже: {}",
                    request.questionSetId(),
                    e.getMessage()
            );
            logger.debug("ML job не готов, подробности", e);
            throw e;
        } catch (GenerationRetryableException e) {
            metricsService.getKafkaProcessingTimer().record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            logger.warn(
                    "Временная ошибка обработки Kafka-сообщения quiz-generation для questionSetId={}. Kafka повторит позже: {}",
                    request.questionSetId(),
                    e.getMessage()
            );
            logger.debug("Временная ошибка quiz-generation, подробности", e);
            throw e;
        } catch (Exception e) {
            metricsService.getKafkaProcessingTimer().record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            logger.error("Ошибка обработки Kafka-сообщения quiz-generation для questionSetId={}", request.questionSetId(), e);
            throw e;
        }
    }

    @DltHandler
    public void onDlt(QuizGenerationRequestMessage request) {
        if (request == null) {
            metricsService.recordKafkaDltMessage();
            return;
        }

        questionGenerationService.markKafkaGenerationRetriesExhausted(request);
        metricsService.recordKafkaDltMessage();
        logger.error("Kafka retry исчерпан, генерация помечена FAILED для questionSetId={}", request.questionSetId());
    }
}

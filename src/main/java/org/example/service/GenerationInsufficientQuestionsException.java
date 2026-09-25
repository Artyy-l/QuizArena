package org.example.service;

/**
 * Генерация завершилась, но не создала нужное число корректных вопросов.
 * Набор остаётся в состоянии FAILED, чтобы его статус не вернулся к READY.
 */
public class GenerationInsufficientQuestionsException extends GenerationNonRetryableException {
    public GenerationInsufficientQuestionsException(String message) {
        super(message);
    }
}

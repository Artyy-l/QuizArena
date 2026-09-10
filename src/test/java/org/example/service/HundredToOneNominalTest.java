package org.example.service;

import org.example.dto.ml.MlQuestionOptionDTO;
import org.example.model.AnswerOption;
import org.example.model.QuestionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HundredToOneNominalTest {
    private static final List<String> CORRECT_IDS = List.of("A", "B", "C", "D", "E");

    @Test
    void acceptsDistinctAiScoresForPopularAnswers() {
        assertTrue(QuestionGenerationService.isValidMlQuestion(
                QuestionType.HUNDRED_TO_ONE, validOptions(), CORRECT_IDS));
    }

    @Test
    void rejectsMissingOrInvalidAiScores() {
        List<MlQuestionOptionDTO> missing = validOptions();
        missing.set(0, new MlQuestionOptionDTO("A", "Ответ A", null));
        assertFalse(QuestionGenerationService.isValidMlQuestion(
                QuestionType.HUNDRED_TO_ONE, missing, CORRECT_IDS));

        List<MlQuestionOptionDTO> duplicate = validOptions();
        duplicate.set(1, new MlQuestionOptionDTO("B", "Ответ B", new BigDecimal("4.5")));
        assertFalse(QuestionGenerationService.isValidMlQuestion(
                QuestionType.HUNDRED_TO_ONE, duplicate, CORRECT_IDS));

        List<MlQuestionOptionDTO> wrongPositive = validOptions();
        wrongPositive.set(5, new MlQuestionOptionDTO("F", "Ответ F", BigDecimal.ONE));
        assertFalse(QuestionGenerationService.isValidMlQuestion(
                QuestionType.HUNDRED_TO_ONE, wrongPositive, CORRECT_IDS));

        List<MlQuestionOptionDTO> invalidStep = validOptions();
        invalidStep.set(0, new MlQuestionOptionDTO("A", "Ответ A", new BigDecimal("4.7")));
        assertFalse(QuestionGenerationService.isValidMlQuestion(
                QuestionType.HUNDRED_TO_ONE, invalidStep, CORRECT_IDS));
    }

    @Test
    void scoringUsesStoredAiScoresInsteadOfAttemptRandomness() {
        List<AnswerOption> options = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            AnswerOption option = new AnswerOption();
            option.setId((long) i + 1);
            option.setCorrect(i < 5);
            option.setNominal(new BigDecimal(i < 5 ? String.valueOf(5 - i) : "0"));
            options.add(option);
        }

        Map<Long, BigDecimal> first = AttemptService.resolveHundredToOneNominals(options);
        Map<Long, BigDecimal> second = AttemptService.resolveHundredToOneNominals(options);
        assertEquals(new BigDecimal("5"), first.get(1L));
        assertEquals(BigDecimal.ZERO, first.get(8L));
        assertEquals(first, second);
    }

    @Test
    void legacyQuestionsHaveStableNonRandomFallback() {
        AnswerOption correct = new AnswerOption();
        correct.setId(1L);
        correct.setCorrect(true);
        AnswerOption wrong = new AnswerOption();
        wrong.setId(2L);

        Map<Long, BigDecimal> nominals = AttemptService.resolveHundredToOneNominals(List.of(correct, wrong));
        assertEquals(BigDecimal.valueOf(5), nominals.get(1L));
        assertEquals(BigDecimal.ZERO, nominals.get(2L));
    }

    private List<MlQuestionOptionDTO> validOptions() {
        List<MlQuestionOptionDTO> options = new ArrayList<>();
        String[] scores = {"4.5", "3", "5", "1.5", "2", "0", "0", "0"};
        for (int i = 0; i < scores.length; i++) {
            String id = String.valueOf((char) ('A' + i));
            options.add(new MlQuestionOptionDTO(id, "Ответ " + id, new BigDecimal(scores[i])));
        }
        return options;
    }
}

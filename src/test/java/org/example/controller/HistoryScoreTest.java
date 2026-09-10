package org.example.controller;

import org.example.model.AttemptQuestion;
import org.example.model.Question;
import org.example.model.QuestionType;
import org.example.model.Quiz;
import org.example.model.UserQuizAttempt;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HistoryScoreTest {
    @Test
    void usesBaseScoreAndSubtractsStakeBonusForLegacyAttempts() {
        UserQuizAttempt legacyAttempt = new UserQuizAttempt();
        legacyAttempt.setScore(18L);
        legacyAttempt.setCatStakeBonus(7);

        assertEquals(11, PageController.getHistoryScore(legacyAttempt));

        legacyAttempt.setBaseScore(13L);
        assertEquals(13, PageController.getHistoryScore(legacyAttempt));
    }

    @Test
    void calculatesMaximumAsFiveForHundredToOneAndOneForOtherQuestions() {
        UserQuizAttempt attempt = new UserQuizAttempt();
        Question hundredToOne = new Question();
        hundredToOne.setType(QuestionType.HUNDRED_TO_ONE);
        Question singleChoice = new Question();
        singleChoice.setType(QuestionType.SINGLE_CHOICE);
        attempt.setAttemptQuestions(List.of(
                new AttemptQuestion(attempt, hundredToOne, 0),
                new AttemptQuestion(attempt, singleChoice, 1)
        ));

        assertEquals(6, PageController.getMaximumScore(attempt));
    }

    @Test
    void fallsBackToQuizQuestionCountForAttemptsWithoutAssignedQuestions() {
        Quiz quiz = new Quiz();
        quiz.setQuestionNumber(8);
        quiz.setDefaultQuestionType(QuestionType.HUNDRED_TO_ONE);
        UserQuizAttempt attempt = new UserQuizAttempt();
        attempt.setQuiz(quiz);

        assertEquals(40, PageController.getMaximumScore(attempt));
    }
}

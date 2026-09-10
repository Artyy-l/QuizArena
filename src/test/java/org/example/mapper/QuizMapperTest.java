package org.example.mapper;

import org.example.dto.response.quiz.QuizDTO;
import org.example.dto.response.quiz.QuizDetailsDTO;
import org.example.model.Quiz;
import org.example.model.User;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class QuizMapperTest {
    @Test
    void mapsEntityWithQuestionDuration() {
        Quiz quiz = new Quiz();
        quiz.setId(7L);
        quiz.setName("Математика");
        quiz.setCreatedBy(new User(3L, "author", "hash"));
        quiz.setQuestionNumber(4);
        quiz.setTimePerQuestion(Duration.ofSeconds(45));
        quiz.setPrivate(true);
        quiz.setStatic(false);
        quiz.setCreatedAt(Instant.parse("2026-01-01T12:00:00Z"));

        QuizDTO result = QuizMapper.fromEntity(quiz);

        assertEquals(4, result.questionCount());
        assertEquals(180, result.timeLimit());
        assertEquals(45, result.timePerQuestion());
        assertEquals(false, result.isPublic());
        assertEquals(LocalDateTime.ofInstant(quiz.getCreatedAt(), ZoneId.systemDefault()), result.createdAt());
    }

    @Test
    void mapsEntityWithoutQuestionDuration() {
        Quiz quiz = new Quiz();
        quiz.setCreatedBy(new User(3L, "author", "hash"));

        QuizDTO result = QuizMapper.fromEntity(quiz);

        assertEquals(0, result.questionCount());
        assertNull(result.timeLimit());
        assertNull(result.timePerQuestion());
    }

    @Test
    void mapsDetailsWithoutQuestions() {
        QuizDetailsDTO details = new QuizDetailsDTO(
                7L, "Математика", null, "author", null, List.of(), 10,
                300, 30, true, false, null, null, null
        );

        QuizDTO result = QuizMapper.fromDetails(details);

        assertEquals(0, result.questionCount());
        assertEquals(300, result.timeLimit());
        assertEquals("author", result.author());
    }
}

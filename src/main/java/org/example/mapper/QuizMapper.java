package org.example.mapper;

import org.example.dto.response.quiz.QuizDTO;
import org.example.dto.response.quiz.QuizDetailsDTO;
import org.example.model.Quiz;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public final class QuizMapper {
    private QuizMapper() {
    }

    public static QuizDTO fromEntity(Quiz quiz) {
        int questionCount = quiz.getQuestionNumber() != null ? quiz.getQuestionNumber() : 0;
        Integer timePerQuestion = null;
        Integer timeLimit = null;
        if (quiz.getTimePerQuestion() != null && quiz.getTimePerQuestion().getSeconds() > 0) {
            long seconds = quiz.getTimePerQuestion().getSeconds();
            timePerQuestion = (int) seconds;
            if (questionCount > 0) {
                timeLimit = (int) (seconds * questionCount);
            }
        }

        return new QuizDTO(
                quiz.getId(),
                quiz.getName(),
                quiz.getCreatedBy().getLogin(),
                questionCount,
                timeLimit,
                timePerQuestion,
                !quiz.isPrivate(),
                quiz.isStatic(),
                toLocalDateTime(quiz.getCreatedAt())
        );
    }

    public static QuizDTO fromDetails(QuizDetailsDTO details) {
        return new QuizDTO(
                details.id(),
                details.name(),
                details.author(),
                details.questions() != null ? details.questions().size() : 0,
                details.timeLimit(),
                details.timePerQuestion(),
                details.isPublic(),
                details.isStatic(),
                details.createdAt()
        );
    }

    private static LocalDateTime toLocalDateTime(Instant instant) {
        return instant != null
                ? LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
                : null;
    }
}

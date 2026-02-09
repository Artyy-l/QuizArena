package org.example.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Модель для хранения ответов пользователей на вопросы квиза.
 */
@Entity
@Table(name = "user_answers")
@Getter
@Setter
public class UserAnswer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id", nullable = false)
    private UserQuizAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_answer_id", nullable = true)
    private AnswerOption selectedAnswer;

    @Column(name = "is_correct")
    private Boolean isCorrect;

    @Column(name = "answered_at")
    private Instant answeredAt;

    @Column(name = "time_spent_seconds")
    private Integer timeSpentSeconds;

    public UserAnswer() {}

    public UserAnswer(Long id, UserQuizAttempt attempt, Question question, 
                     AnswerOption selectedAnswer, Boolean isCorrect, 
                     Instant answeredAt, Integer timeSpentSeconds) {
        this.id = id;
        this.attempt = attempt;
        this.question = question;
        this.selectedAnswer = selectedAnswer;
        this.isCorrect = isCorrect;
        this.answeredAt = answeredAt;
        this.timeSpentSeconds = timeSpentSeconds;
    }
}


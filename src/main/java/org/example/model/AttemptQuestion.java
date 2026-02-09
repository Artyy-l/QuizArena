package org.example.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Модель для хранения выбранных вопросов для каждой попытки прохождения квиза.
 * Позволяет сохранить, какие именно вопросы были выбраны для конкретной попытки,
 * что важно для статичных и обновляемых квизов.
 */
@Entity
@Table(name = "attempt_questions")
@Getter
@Setter
public class AttemptQuestion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id", nullable = false)
    private UserQuizAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "question_order", nullable = false)
    private Integer questionOrder;

    public AttemptQuestion() {}

    public AttemptQuestion(UserQuizAttempt attempt, Question question, Integer questionOrder) {
        this.attempt = attempt;
        this.question = question;
        this.questionOrder = questionOrder;
    }
}


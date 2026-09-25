package org.example.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

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

    @Column(name = "accuracy_ratio")
    private Double accuracyRatio;

    public UserAnswer() {}

    public UserAnswer(Long id, UserQuizAttempt attempt, Question question, 
                     AnswerOption selectedAnswer, Boolean isCorrect) {
        this.id = id;
        this.attempt = attempt;
        this.question = question;
        this.selectedAnswer = selectedAnswer;
        this.isCorrect = isCorrect;
    }
}

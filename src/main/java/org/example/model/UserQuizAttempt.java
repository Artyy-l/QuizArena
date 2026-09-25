package org.example.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "user_quiz_attempts")
@Getter
@Setter
public class UserQuizAttempt {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "quiz_id", nullable = false)
  private Quiz quiz;

  @Column(name = "start_time")
  private Instant startTime;

  @Column(name = "finish_time")
  private Instant finishTime;

  private Long score;

  @Column(name = "base_score")
  private Long baseScore;

  @Column(name = "accuracy_percent")
  private Integer accuracyPercent;

  @Column(name = "is_completed")
  private boolean isCompleted;

  @Column(name = "session_id", length = 100)
  private String sessionId;

  @Column(name = "current_question_id")
  private Long currentQuestionId;

  @Column(name = "current_question_started_at")
  private Instant currentQuestionStartedAt;

  @Column(name = "current_question_deadline_at")
  private Instant currentQuestionDeadlineAt;

  @Column(name = "cat_stake")
  private Integer catStake;

  @Column(name = "cat_stake_bonus")
  private Integer catStakeBonus;

  @OneToMany(mappedBy = "attempt", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<AttemptQuestion> attemptQuestions = new ArrayList<>();

  public UserQuizAttempt() {}

  public UserQuizAttempt(Long id, User user, Quiz quiz, Instant startTime,
                         Instant finishTime, Long score, boolean isCompleted) {
    this.id = id;
    this.user = user;
    this.quiz = quiz;
    this.startTime = startTime;
    this.finishTime = finishTime;
    this.score = score;
    this.isCompleted = isCompleted;
  }

  public UserQuizAttempt(Long id, User user, Quiz quiz, Instant startTime,
                         Instant finishTime, Long score, boolean isCompleted, String sessionId) {
    this.id = id;
    this.user = user;
    this.quiz = quiz;
    this.startTime = startTime;
    this.finishTime = finishTime;
    this.score = score;
    this.isCompleted = isCompleted;
    this.sessionId = sessionId;
  }
}

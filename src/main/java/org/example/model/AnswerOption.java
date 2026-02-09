package org.example.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "answer_options")
@Getter
@Setter
public class AnswerOption {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "question_id", nullable = false)
  private Question question;

  @Column(columnDefinition = "TEXT")
  private String text;

  @Column(name = "is_correct")
  private boolean isCorrect;

  @Column(name = "is_na_option")
  private boolean isNaOption;

  public AnswerOption() {}

  public AnswerOption(Long id, Question question, String text, boolean isCorrect, boolean isNaOption) {
    this.id = id;
    this.question = question;
    this.text = text;
    this.isCorrect = isCorrect;
    this.isNaOption = isNaOption;
  }
}

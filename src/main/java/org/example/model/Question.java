package org.example.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "questions")
@Getter
@Setter
public class Question {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "quiz_id", nullable = false)
  private Quiz quiz;

  @Column(columnDefinition = "TEXT")
  private String text;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private QuestionType type;

  @Column(columnDefinition = "TEXT")
  private String explanation;

  @Lob
  private byte[] image;

  @Column(name = "is_generated")
  private Boolean isGenerated;

  @Column(name = "generation_set_id")
  private Long generationSetId;

  @Column(name = "is_valid")
  private Boolean isValid;

  @Column(name = "is_duplicate")
  private Boolean isDuplicate;

  @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<AnswerOption> answerOptions = new ArrayList<>();

  public Question() {
    this.answerOptions = new ArrayList<>();
  }

  public Question(Long id, Quiz quiz, String text, QuestionType type, String explanation, byte[] image) {
    this.id = id;
    this.quiz = quiz;
    this.text = text;
    this.type = type;
    this.explanation = explanation;
    this.image = image;
    this.answerOptions = new ArrayList<>();
  }
}

package org.example.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "quizzes")
@Getter
@Setter
public class Quiz {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String name;

  @Column(columnDefinition = "TEXT")
  private String prompt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "created_by", nullable = false)
  private User createdBy;

  @Column(name = "has_material")
  private boolean hasMaterial;

  @Column(name = "material_url")
  private String materialUrl;

  @Column(name = "question_number")
  private Integer questionNumber;

  @Column(name = "time_per_question_seconds")
  @Convert(converter = DurationSecondsConverter.class)
  private Duration timePerQuestion;

  @Column(name = "is_private")
  private boolean isPrivate;

  @Column(name = "is_static")
  private boolean isStatic;

  @Column(name = "created_at")
  private Instant createdAt;

  @OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<Question> questions = new ArrayList<>();

  public Quiz() {
    this.questions = new ArrayList<>();
  }

  public Quiz(Long id, String name, String prompt, User createdBy, boolean hasMaterial,
              String materialUrl, Duration timePerQuestion, boolean isPrivate,
              boolean isStatic, Instant createdAt) {
    this.id = id;
    this.name = name;
    this.prompt = prompt;
    this.createdBy = createdBy;
    this.hasMaterial = hasMaterial;
    this.materialUrl = materialUrl;
    this.timePerQuestion = timePerQuestion;
    this.isPrivate = isPrivate;
    this.isStatic = isStatic;
    this.createdAt = createdAt;
    this.questions = new ArrayList<>();
  }
}

package org.example.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Модель для хранения наборов сгенерированных вопросов.
 * Путь: src/main/java/org/example/model/GenerationSet.java
 */
@Entity
@Table(name = "generation_sets")
@Getter
@Setter
public class GenerationSet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @Column(name = "prompt", columnDefinition = "TEXT")
    private String prompt;

    @Column(name = "status", nullable = false, length = 20)
    private String status; // "GENERATING", "VALIDATING", "DEDUPLICATING", "READY", "FAILED"

    @Column(name = "generated_count")
    private Integer generatedCount;

    @Column(name = "valid_count")
    private Integer validCount;

    @Column(name = "duplicate_count")
    private Integer duplicateCount;

    @Column(name = "final_count")
    private Integer finalCount;

    @Column(name = "created_at")
    private Instant createdAt;

    public GenerationSet() {}

    public GenerationSet(Long id, Quiz quiz, String prompt, String status, 
                        Integer generatedCount, Integer validCount, 
                        Integer duplicateCount, Integer finalCount, Instant createdAt) {
        this.id = id;
        this.quiz = quiz;
        this.prompt = prompt;
        this.status = status;
        this.generatedCount = generatedCount;
        this.validCount = validCount;
        this.duplicateCount = duplicateCount;
        this.finalCount = finalCount;
        this.createdAt = createdAt;
    }
}


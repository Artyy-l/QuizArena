package org.example.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "multiplayer_sessions")
@Getter
@Setter
public class MultiplayerSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", unique = true, nullable = false, length = 100)
    private String sessionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_user_id", nullable = false)
    private User hostUser;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "cat_question_index")
    private Integer catQuestionIndex;

    public MultiplayerSession() {}

    public MultiplayerSession(Long id, String sessionId, Quiz quiz, User hostUser,
                             String status, Instant createdAt, Instant startedAt, Instant finishedAt,
                             Integer catQuestionIndex) {
        this.id = id;
        this.sessionId = sessionId;
        this.quiz = quiz;
        this.hostUser = hostUser;
        this.status = status;
        this.createdAt = createdAt;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.catQuestionIndex = catQuestionIndex;
    }
}

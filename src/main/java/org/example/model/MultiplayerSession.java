package org.example.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Модель для хранения сессий совместного прохождения квизов.
 */
@Entity
@Table(name = "multiplayer_sessions")
@Getter
@Setter
public class MultiplayerSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", unique = true, nullable = false, length = 100)
    private String sessionId; // UUID или уникальный идентификатор

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_user_id", nullable = false)
    private User hostUser;

    @Column(name = "status", nullable = false, length = 20)
    private String status; // "WAITING", "STARTED", "FINISHED", "CANCELLED"

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    public MultiplayerSession() {}

    public MultiplayerSession(Long id, String sessionId, Quiz quiz, User hostUser, 
                             String status, Instant createdAt, Instant startedAt, Instant finishedAt) {
        this.id = id;
        this.sessionId = sessionId;
        this.quiz = quiz;
        this.hostUser = hostUser;
        this.status = status;
        this.createdAt = createdAt;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }
}


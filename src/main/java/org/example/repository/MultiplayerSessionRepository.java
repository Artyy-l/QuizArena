package org.example.repository;

import org.example.model.MultiplayerSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MultiplayerSessionRepository extends JpaRepository<MultiplayerSession, Long> {
    Optional<MultiplayerSession> findBySessionId(String sessionId);

    List<MultiplayerSession> findByHostUserId(Long userId);

    List<MultiplayerSession> findByStatus(String status);

    boolean existsBySessionId(String sessionId);

    @Query("SELECT m FROM MultiplayerSession m WHERE m.quiz.id = :quizId")
    List<MultiplayerSession> findByQuizId(@Param("quizId") Long quizId);
}

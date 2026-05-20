package org.example.repository;

import org.example.model.UserQuizAttempt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserQuizAttemptRepository extends JpaRepository<UserQuizAttempt, Long> {
    Page<UserQuizAttempt> findByUserId(Long userId, Pageable pageable);
    List<UserQuizAttempt> findByQuizId(Long quizId);

    @Query("SELECT u FROM UserQuizAttempt u JOIN FETCH u.quiz q WHERE u.user.id = :userId " +
           "AND u.isCompleted = true AND u.startTime IS NOT NULL AND u.finishTime IS NOT NULL " +
           "ORDER BY u.finishTime DESC")
    List<UserQuizAttempt> findCompletedHistoryByUserId(@Param("userId") Long userId);
    
    @Query("SELECT u FROM UserQuizAttempt u WHERE u.quiz.id = :quizId AND u.isCompleted = true " +
           "AND u.startTime IS NOT NULL AND u.finishTime IS NOT NULL " +
           "ORDER BY u.score DESC NULLS LAST, u.finishTime ASC")
    Page<UserQuizAttempt> findCompletedByQuizIdOrderByScoreDesc(@Param("quizId") Long quizId, Pageable pageable);

    @Query("SELECT u FROM UserQuizAttempt u WHERE u.quiz.id = :quizId AND u.isCompleted = true " +
           "AND u.startTime IS NOT NULL AND u.finishTime IS NOT NULL")
    Page<UserQuizAttempt> findCompletedByQuizId(@Param("quizId") Long quizId, Pageable pageable);

    @Query("SELECT u FROM UserQuizAttempt u WHERE u.quiz.id = :quizId AND u.sessionId = :sessionId " +
           "AND u.isCompleted = true AND u.startTime IS NOT NULL AND u.finishTime IS NOT NULL")
    Page<UserQuizAttempt> findCompletedByQuizIdAndSessionId(@Param("quizId") Long quizId,
                                                            @Param("sessionId") String sessionId,
                                                            Pageable pageable);

    @Query("SELECT u FROM UserQuizAttempt u WHERE u.quiz.id = :quizId AND u.isCompleted = true " +
           "AND u.sessionId IS NULL AND u.startTime IS NOT NULL AND u.finishTime IS NOT NULL " +
           "ORDER BY u.score DESC NULLS LAST, u.finishTime ASC")
    Page<UserQuizAttempt> findCompletedSoloByQuizIdOrderByScoreDesc(@Param("quizId") Long quizId, Pageable pageable);

    @Query("SELECT u FROM UserQuizAttempt u WHERE u.quiz.id = :quizId AND u.isCompleted = true " +
           "AND u.sessionId IS NOT NULL AND u.startTime IS NOT NULL AND u.finishTime IS NOT NULL " +
           "ORDER BY u.score DESC NULLS LAST, u.finishTime ASC")
    Page<UserQuizAttempt> findCompletedMultiplayerByQuizIdOrderByScoreDesc(@Param("quizId") Long quizId, Pageable pageable);

    @Query("SELECT u.quiz.id FROM UserQuizAttempt u WHERE u.id = :attemptId")
    Long findQuizIdByAttemptId(@Param("attemptId") Long attemptId);
  
    @Query("SELECT u FROM UserQuizAttempt u WHERE u.quiz.id = :quizId AND u.isCompleted = true " +
           "AND u.startTime IS NOT NULL AND u.finishTime IS NOT NULL " +
           "AND u.score = (SELECT MAX(u2.score) FROM UserQuizAttempt u2 WHERE u2.quiz.id = :quizId " +
           "AND u2.user.id = u.user.id AND u2.isCompleted = true AND u2.startTime IS NOT NULL AND u2.finishTime IS NOT NULL) " +
           "ORDER BY u.score DESC NULLS LAST, u.finishTime ASC")
    Page<UserQuizAttempt> findBestAttemptsByQuizId(@Param("quizId") Long quizId, Pageable pageable);

    @Query("SELECT u FROM UserQuizAttempt u WHERE u.quiz.id = :quizId AND u.isCompleted = true " +
           "AND u.sessionId IS NULL AND u.startTime IS NOT NULL AND u.finishTime IS NOT NULL " +
           "AND u.score = (SELECT MAX(u2.score) FROM UserQuizAttempt u2 WHERE u2.quiz.id = :quizId " +
           "AND u2.user.id = u.user.id AND u2.sessionId IS NULL AND u2.isCompleted = true " +
           "AND u2.startTime IS NOT NULL AND u2.finishTime IS NOT NULL) " +
           "ORDER BY u.score DESC NULLS LAST, u.finishTime ASC")
    Page<UserQuizAttempt> findBestSoloAttemptsByQuizId(@Param("quizId") Long quizId, Pageable pageable);

    @Query("SELECT u FROM UserQuizAttempt u WHERE u.quiz.id = :quizId AND u.isCompleted = true " +
           "AND u.sessionId IS NOT NULL AND u.startTime IS NOT NULL AND u.finishTime IS NOT NULL " +
           "AND u.score = (SELECT MAX(u2.score) FROM UserQuizAttempt u2 WHERE u2.quiz.id = :quizId " +
           "AND u2.user.id = u.user.id AND u2.sessionId IS NOT NULL AND u2.isCompleted = true " +
           "AND u2.startTime IS NOT NULL AND u2.finishTime IS NOT NULL) " +
           "ORDER BY u.score DESC NULLS LAST, u.finishTime ASC")
    Page<UserQuizAttempt> findBestMultiplayerAttemptsByQuizId(@Param("quizId") Long quizId, Pageable pageable);

    long countByUserId(Long userId);

    @Query("SELECT COUNT(u) FROM UserQuizAttempt u WHERE u.user.id = :userId AND u.quiz.id = :quizId " +
           "AND u.isCompleted = true AND u.startTime IS NOT NULL AND u.finishTime IS NOT NULL")
    long countCompletedByUserIdAndQuizId(@Param("userId") Long userId, @Param("quizId") Long quizId);

    @Query("SELECT u.quiz.id, COUNT(u) FROM UserQuizAttempt u WHERE u.quiz.id IN :quizIds " +
           "AND u.isCompleted = true AND u.startTime IS NOT NULL AND u.finishTime IS NOT NULL " +
           "GROUP BY u.quiz.id")
    List<Object[]> countCompletedByQuizIds(@Param("quizIds") List<Long> quizIds);

    @Query("SELECT COUNT(u) FROM UserQuizAttempt u WHERE u.user.id = :userId AND u.quiz.id = :quizId " +
           "AND u.isCompleted = true AND u.startTime IS NOT NULL AND u.finishTime IS NOT NULL " +
           "AND u.id <= :attemptId")
    long countCompletedByUserIdAndQuizIdUpToAttemptId(@Param("userId") Long userId,
                                                       @Param("quizId") Long quizId,
                                                       @Param("attemptId") Long attemptId);

    long countByIsCompletedFalse();
    
    List<UserQuizAttempt> findBySessionId(String sessionId);
    
    @Query("SELECT u FROM UserQuizAttempt u JOIN FETCH u.user WHERE u.sessionId = :sessionId")
    List<UserQuizAttempt> findBySessionIdWithUser(@Param("sessionId") String sessionId);
    
    UserQuizAttempt findByUserIdAndQuizIdAndSessionId(Long userId, Long quizId, String sessionId);

    UserQuizAttempt findTopByUserIdAndQuizIdAndSessionIdOrderByIdDesc(Long userId, Long quizId, String sessionId);

    UserQuizAttempt findTopByUserIdAndQuizIdAndSessionIdIsNullAndIsCompletedFalseOrderByIdDesc(Long userId, Long quizId);
    
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM user_quiz_attempts WHERE user_id = :userId AND quiz_id = :quizId AND session_id = :sessionId", nativeQuery = true)
    int deleteByUserIdAndQuizIdAndSessionId(@Param("userId") Long userId, @Param("quizId") Long quizId, @Param("sessionId") String sessionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM user_quiz_attempts WHERE session_id = :sessionId", nativeQuery = true)
    int deleteBySessionId(@Param("sessionId") String sessionId);
}

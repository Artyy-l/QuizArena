package org.example.repository;

import org.example.model.UserAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserAnswerRepository extends JpaRepository<UserAnswer, Long> {
    List<UserAnswer> findByAttemptId(Long attemptId);

    Optional<UserAnswer> findByAttemptIdAndQuestionId(Long attemptId, Long questionId);

    long countByAttemptId(Long attemptId);

    long countByAttemptIdAndIsCorrectTrue(Long attemptId);

    boolean existsByAttemptIdAndQuestionId(Long attemptId, Long questionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM user_answers WHERE question_id IN (SELECT id FROM questions WHERE quiz_id = :quizId)", nativeQuery = true)
    void deleteByQuestionQuizId(@Param("quizId") Long quizId);
    
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE user_answers SET selected_answer_id = NULL WHERE selected_answer_id IN (SELECT ao.id FROM answer_options ao JOIN questions q ON ao.question_id = q.id WHERE q.quiz_id = :quizId)", nativeQuery = true)
    void nullifySelectedAnswerReferences(@Param("quizId") Long quizId);
    
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM user_answers WHERE attempt_id IN (SELECT id FROM user_quiz_attempts WHERE user_id = :userId AND quiz_id = :quizId AND session_id = :sessionId)", nativeQuery = true)
    int deleteByUserIdAndQuizIdAndSessionId(@Param("userId") Long userId, @Param("quizId") Long quizId, @Param("sessionId") String sessionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM user_answers WHERE attempt_id IN (SELECT id FROM user_quiz_attempts WHERE session_id = :sessionId)", nativeQuery = true)
    int deleteBySessionId(@Param("sessionId") String sessionId);
}

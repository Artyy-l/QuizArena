package org.example.repository;

import org.example.model.AttemptQuestion;
import org.example.model.UserQuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttemptQuestionRepository extends JpaRepository<AttemptQuestion, Long> {
    @Query("SELECT aq FROM AttemptQuestion aq WHERE aq.attempt.id = :attemptId ORDER BY aq.questionOrder ASC")
    List<AttemptQuestion> findByAttemptIdOrderByQuestionOrder(@Param("attemptId") Long attemptId);

    @Query("SELECT aq.question.id FROM AttemptQuestion aq WHERE aq.attempt.id = :attemptId ORDER BY aq.questionOrder ASC")
    List<Long> findQuestionIdsByAttemptId(@Param("attemptId") Long attemptId);

    @Query("SELECT aq FROM AttemptQuestion aq WHERE aq.attempt.id = :attemptId AND aq.questionOrder = :order")
    Optional<AttemptQuestion> findByAttemptIdAndOrder(@Param("attemptId") Long attemptId, @Param("order") Integer order);

    void deleteByAttempt(UserQuizAttempt attempt);

    boolean existsByAttemptIdAndQuestionId(Long attemptId, Long questionId);
}

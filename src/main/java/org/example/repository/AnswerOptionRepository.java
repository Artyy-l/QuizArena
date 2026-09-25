package org.example.repository;

import org.example.model.AnswerOption;
import org.example.model.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnswerOptionRepository extends JpaRepository<AnswerOption, Long> {
    List<AnswerOption> findByQuestion(Question question);
    List<AnswerOption> findByQuestionId(Long questionId);
    Optional<AnswerOption> findByQuestionAndIsCorrectTrue(Question question);
    Optional<AnswerOption> findByQuestionIdAndIsCorrectTrue(Long questionId);
    void deleteByQuestionId(Long questionId);
}

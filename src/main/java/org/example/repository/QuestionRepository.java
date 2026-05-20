package org.example.repository;

import org.example.model.Question;
import org.example.model.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {
    List<Question> findByQuiz(Quiz quiz);
    List<Question> findByQuizId(Long quizId);
    Optional<Question> findByIdAndQuizId(Long id, Long quizId);
    void deleteByQuizId(Long quizId);
    void deleteByGenerationSetId(Long generationSetId);
    long countByQuizId(Long quizId);
}

package org.example.repository;

import org.example.model.GenerationSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GenerationSetRepository extends JpaRepository<GenerationSet, Long> {
    Optional<GenerationSet> findByQuizId(Long quizId);

    Optional<GenerationSet> findTopByQuizIdOrderByCreatedAtDesc(Long quizId);

    List<GenerationSet> findByStatus(String status);

    List<GenerationSet> findAllByQuizId(Long quizId);
}

package org.example.repository;

import org.example.model.Quiz;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuizRepository extends JpaRepository<Quiz, Long> {
    Page<Quiz> findByIsPrivateFalse(Pageable pageable);
    Page<Quiz> findByCreatedById(Long userId, Pageable pageable);
    List<Quiz> findByCreatedById(Long userId);

    @Query("SELECT q FROM Quiz q WHERE q.id = :id AND q.isPrivate = false")
    Optional<Quiz> findPublicById(@Param("id") Long id);

    @Query("SELECT q FROM Quiz q WHERE q.isPrivate = false AND " +
           "LOWER(q.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<Quiz> searchPublicQuizzes(@Param("searchTerm") String searchTerm, Pageable pageable);

    @Query(value = "SELECT q FROM Quiz q LEFT JOIN UserQuizAttempt u ON u.quiz = q " +
           "AND u.isCompleted = true AND u.startTime IS NOT NULL AND u.finishTime IS NOT NULL " +
           "WHERE q.isPrivate = false AND " +
           "(:searchTerm IS NULL OR :searchTerm = '' OR LOWER(q.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
           "GROUP BY q ORDER BY COUNT(u) ASC, q.createdAt DESC",
           countQuery = "SELECT COUNT(q) FROM Quiz q WHERE q.isPrivate = false AND " +
           "(:searchTerm IS NULL OR :searchTerm = '' OR LOWER(q.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<Quiz> searchPublicQuizzesOrderByCompletedAttemptsAsc(@Param("searchTerm") String searchTerm, Pageable pageable);

    @Query(value = "SELECT q FROM Quiz q LEFT JOIN UserQuizAttempt u ON u.quiz = q " +
           "AND u.isCompleted = true AND u.startTime IS NOT NULL AND u.finishTime IS NOT NULL " +
           "WHERE q.isPrivate = false AND " +
           "(:searchTerm IS NULL OR :searchTerm = '' OR LOWER(q.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
           "GROUP BY q ORDER BY COUNT(u) DESC, q.createdAt DESC",
           countQuery = "SELECT COUNT(q) FROM Quiz q WHERE q.isPrivate = false AND " +
           "(:searchTerm IS NULL OR :searchTerm = '' OR LOWER(q.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<Quiz> searchPublicQuizzesOrderByCompletedAttemptsDesc(@Param("searchTerm") String searchTerm, Pageable pageable);

    @EntityGraph(attributePaths = "createdBy")
    @Query("SELECT q FROM Quiz q WHERE q.createdBy.id = :userId AND " +
           "(:searchTerm IS NULL OR :searchTerm = '' OR LOWER(q.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) AND " +
           "(:visibility = 'all' OR (:visibility = 'public' AND q.isPrivate = false) OR (:visibility = 'private' AND q.isPrivate = true))")
    Page<Quiz> searchCreatedQuizzes(@Param("userId") Long userId,
                                    @Param("searchTerm") String searchTerm,
                                    @Param("visibility") String visibility,
                                    Pageable pageable);

    @EntityGraph(attributePaths = "createdBy")
    @Query(value = "SELECT q FROM Quiz q LEFT JOIN UserQuizAttempt u ON u.quiz = q " +
           "AND u.isCompleted = true AND u.startTime IS NOT NULL AND u.finishTime IS NOT NULL " +
           "WHERE q.createdBy.id = :userId AND " +
           "(:searchTerm IS NULL OR :searchTerm = '' OR LOWER(q.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) AND " +
           "(:visibility = 'all' OR (:visibility = 'public' AND q.isPrivate = false) OR (:visibility = 'private' AND q.isPrivate = true)) " +
           "GROUP BY q ORDER BY COUNT(u) ASC, q.createdAt DESC",
           countQuery = "SELECT COUNT(q) FROM Quiz q WHERE q.createdBy.id = :userId AND " +
           "(:searchTerm IS NULL OR :searchTerm = '' OR LOWER(q.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) AND " +
           "(:visibility = 'all' OR (:visibility = 'public' AND q.isPrivate = false) OR (:visibility = 'private' AND q.isPrivate = true))")
    Page<Quiz> searchCreatedQuizzesOrderByCompletedAttemptsAsc(@Param("userId") Long userId,
                                                               @Param("searchTerm") String searchTerm,
                                                               @Param("visibility") String visibility,
                                                               Pageable pageable);

    @EntityGraph(attributePaths = "createdBy")
    @Query(value = "SELECT q FROM Quiz q LEFT JOIN UserQuizAttempt u ON u.quiz = q " +
           "AND u.isCompleted = true AND u.startTime IS NOT NULL AND u.finishTime IS NOT NULL " +
           "WHERE q.createdBy.id = :userId AND " +
           "(:searchTerm IS NULL OR :searchTerm = '' OR LOWER(q.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) AND " +
           "(:visibility = 'all' OR (:visibility = 'public' AND q.isPrivate = false) OR (:visibility = 'private' AND q.isPrivate = true)) " +
           "GROUP BY q ORDER BY COUNT(u) DESC, q.createdAt DESC",
           countQuery = "SELECT COUNT(q) FROM Quiz q WHERE q.createdBy.id = :userId AND " +
           "(:searchTerm IS NULL OR :searchTerm = '' OR LOWER(q.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) AND " +
           "(:visibility = 'all' OR (:visibility = 'public' AND q.isPrivate = false) OR (:visibility = 'private' AND q.isPrivate = true))")
    Page<Quiz> searchCreatedQuizzesOrderByCompletedAttemptsDesc(@Param("userId") Long userId,
                                                                @Param("searchTerm") String searchTerm,
                                                                @Param("visibility") String visibility,
                                                                Pageable pageable);

    @Query("SELECT COUNT(q) > 0 FROM Quiz q WHERE q.id = :quizId AND q.createdBy.id = :userId")
    boolean isCreator(@Param("quizId") Long quizId, @Param("userId") Long userId);
    
    long countByCreatedById(Long userId);
}

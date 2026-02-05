package com.securenotes.repository;

import com.securenotes.entity.Note;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Note entity operations.
 * Custom queries enforce data isolation at the database level.
 */
@Repository
public interface NoteRepository extends JpaRepository<Note, UUID> {

    /**
     * Find all notes belonging to a specific user.
     * Enforces data isolation by filtering at DB level.
     */
    List<Note> findByUserId(UUID userId);

    /**
     * Find a specific note by ID only if it belongs to the specified user.
     * Critical for access control - prevents unauthorized access.
     */
    Optional<Note> findByIdAndUserId(UUID noteId, UUID userId);

    /**
     * Delete a note only if it belongs to the specified user.
     */
    void deleteByIdAndUserId(UUID noteId, UUID userId);

    /**
     * Check if a note exists and belongs to the specified user.
     */
    boolean existsByIdAndUserId(UUID noteId, UUID userId);

    /**
     * Fetches only note titles for a specific user using native SQL.
     * This query uses Prepared Statement (parameterized query) which prevents SQL Injection.
     * The :userId parameter is safely bound by Spring Data JPA.
     * 
     * @param userId The user's UUID
     * @return List of note titles belonging to the user
     */
    @Query(value = "SELECT n.title FROM notes n WHERE n.user_id = :userId ORDER BY n.created_at DESC", 
           nativeQuery = true)
    List<String> findNoteTitlesByUserId(@Param("userId") UUID userId);

    /**
     * Fetches note titles matching a search term using native SQL with LIKE.
     * Uses Prepared Statement - the searchTerm is safely parameterized, preventing SQL Injection.
     * 
     * @param userId The user's UUID
     * @param searchTerm The search term to match in titles
     * @return List of matching note titles
     */
    @Query(value = "SELECT n.title FROM notes n WHERE n.user_id = :userId AND n.title ILIKE '%' || :searchTerm || '%' ORDER BY n.created_at DESC", 
           nativeQuery = true)
    List<String> searchNoteTitlesByUserId(@Param("userId") UUID userId, @Param("searchTerm") String searchTerm);
}

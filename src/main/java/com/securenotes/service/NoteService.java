package com.securenotes.service;

import com.securenotes.dto.NoteRequest;
import com.securenotes.dto.NoteResponse;
import com.securenotes.entity.Note;
import com.securenotes.entity.User;
import com.securenotes.exception.AccessDeniedException;
import com.securenotes.exception.ResourceNotFoundException;
import com.securenotes.repository.NoteRepository;
import com.securenotes.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for note CRUD operations with strict access control.
 * ALL operations verify that the note belongs to the authenticated user.
 * This is the critical layer for enforcing data isolation.
 */
@Service
public class NoteService {

    private static final Logger logger = LoggerFactory.getLogger(NoteService.class);

    private final NoteRepository noteRepository;
    private final UserRepository userRepository;

    public NoteService(NoteRepository noteRepository, UserRepository userRepository) {
        this.noteRepository = noteRepository;
        this.userRepository = userRepository;
    }

    /**
     * Creates a new note for the authenticated user.
     *
     * @param request Note creation request
     * @param userId The authenticated user's ID
     * @return NoteResponse with created note details
     */
    @Transactional
    public NoteResponse createNote(NoteRequest request, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        Note note = new Note(request.getTitle(), request.getContent(), user);
        Note savedNote = noteRepository.save(note);

        logger.debug("Created note {} for user {}", savedNote.getId(), userId);
        return NoteResponse.fromEntity(savedNote);
    }

    /**
     * Retrieves all notes belonging to the authenticated user.
     * Uses repository method that filters by userId at DB level.
     *
     * @param userId The authenticated user's ID
     * @return List of NoteResponse for user's notes
     */
    @Transactional(readOnly = true)
    public List<NoteResponse> getAllNotesForUser(UUID userId) {
        return noteRepository.findByUserId(userId).stream()
                .map(NoteResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves a specific note by ID, verifying ownership.
     * Returns 404 if note doesn't exist OR doesn't belong to user.
     * This prevents information leakage about other users' notes.
     *
     * @param noteId The note ID to retrieve
     * @param userId The authenticated user's ID
     * @return NoteResponse if note exists and belongs to user
     * @throws ResourceNotFoundException if note not found or not owned by user
     */
    @Transactional(readOnly = true)
    public NoteResponse getNoteById(UUID noteId, UUID userId) {
        Note note = noteRepository.findByIdAndUserId(noteId, userId)
                .orElseThrow(() -> {
                    logger.warn("Unauthorized access attempt: User {} tried to access note {}", userId, noteId);
                    return new ResourceNotFoundException("Note", "id", noteId);
                });

        return NoteResponse.fromEntity(note);
    }

    /**
     * Updates a note, verifying ownership first.
     * Only the note owner can update their notes.
     *
     * @param noteId The note ID to update
     * @param request Update request with new values
     * @param userId The authenticated user's ID
     * @return Updated NoteResponse
     * @throws ResourceNotFoundException if note not found or not owned by user
     */
    @Transactional
    public NoteResponse updateNote(UUID noteId, NoteRequest request, UUID userId) {
        Note note = noteRepository.findByIdAndUserId(noteId, userId)
                .orElseThrow(() -> {
                    logger.warn("Unauthorized update attempt: User {} tried to update note {}", userId, noteId);
                    return new ResourceNotFoundException("Note", "id", noteId);
                });

        note.setTitle(request.getTitle());
        note.setContent(request.getContent());

        Note updatedNote = noteRepository.save(note);
        logger.debug("Updated note {} for user {}", noteId, userId);

        return NoteResponse.fromEntity(updatedNote);
    }

    /**
     * Deletes a note, verifying ownership first.
     * Only the note owner can delete their notes.
     *
     * @param noteId The note ID to delete
     * @param userId The authenticated user's ID
     * @throws ResourceNotFoundException if note not found or not owned by user
     */
    @Transactional
    public void deleteNote(UUID noteId, UUID userId) {
        if (!noteRepository.existsByIdAndUserId(noteId, userId)) {
            logger.warn("Unauthorized delete attempt: User {} tried to delete note {}", userId, noteId);
            throw new ResourceNotFoundException("Note", "id", noteId);
        }

        noteRepository.deleteByIdAndUserId(noteId, userId);
        logger.debug("Deleted note {} for user {}", noteId, userId);
    }

    /**
     * Verifies that a note belongs to the specified user.
     * Used for additional access control checks.
     *
     * @param noteId The note ID to check
     * @param userId The user ID to verify ownership
     * @return true if note belongs to user
     */
    @Transactional(readOnly = true)
    public boolean isNoteOwnedByUser(UUID noteId, UUID userId) {
        return noteRepository.existsByIdAndUserId(noteId, userId);
    }
}

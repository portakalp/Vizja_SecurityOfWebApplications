package com.securenotes.controller;

import com.securenotes.dto.NoteRequest;
import com.securenotes.dto.NoteResponse;
import com.securenotes.security.UserDetailsImpl;
import com.securenotes.service.NoteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for note CRUD operations.
 * All endpoints require authentication and enforce strict data isolation.
 */
@RestController
@RequestMapping("/api/notes")
public class NoteController {

    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    /**
     * Creates a new note for the authenticated user.
     * POST /api/notes
     */
    @PostMapping
    public ResponseEntity<NoteResponse> createNote(
            @Valid @RequestBody NoteRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        
        NoteResponse response = noteService.createNote(request, userDetails.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves all notes for the authenticated user.
     * GET /api/notes
     */
    @GetMapping
    public ResponseEntity<List<NoteResponse>> getAllNotes(
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        
        List<NoteResponse> notes = noteService.getAllNotesForUser(userDetails.getId());
        return ResponseEntity.ok(notes);
    }

    /**
     * Retrieves a specific note by ID.
     * Returns 404 if note doesn't exist or doesn't belong to user.
     * GET /api/notes/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<NoteResponse> getNoteById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        
        NoteResponse response = noteService.getNoteById(id, userDetails.getId());
        return ResponseEntity.ok(response);
    }

    /**
     * Updates a note.
     * Only the note owner can update.
     * PUT /api/notes/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<NoteResponse> updateNote(
            @PathVariable UUID id,
            @Valid @RequestBody NoteRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        
        NoteResponse response = noteService.updateNote(id, request, userDetails.getId());
        return ResponseEntity.ok(response);
    }

    /**
     * Deletes a note.
     * Only the note owner can delete.
     * DELETE /api/notes/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNote(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        
        noteService.deleteNote(id, userDetails.getId());
        return ResponseEntity.noContent().build();
    }
}

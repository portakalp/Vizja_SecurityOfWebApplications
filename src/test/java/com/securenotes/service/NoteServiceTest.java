package com.securenotes.service;

import com.securenotes.dto.NoteRequest;
import com.securenotes.dto.NoteResponse;
import com.securenotes.entity.Note;
import com.securenotes.entity.User;
import com.securenotes.exception.ResourceNotFoundException;
import com.securenotes.repository.NoteRepository;
import com.securenotes.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NoteServiceTest {

    @Mock
    private NoteRepository noteRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private NoteService noteService;

    private User testUser;
    private User otherUser;
    private Note testNote;
    private UUID userId;
    private UUID otherUserId;
    private UUID noteId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        noteId = UUID.randomUUID();

        testUser = new User("testuser", "test@example.com", "hashedPassword");
        testUser.setId(userId);

        otherUser = new User("otheruser", "other@example.com", "hashedPassword");
        otherUser.setId(otherUserId);

        testNote = new Note("Test Title", "Test Content", testUser);
        testNote.setId(noteId);
        testNote.setCreatedAt(Instant.now());
        testNote.setUpdatedAt(Instant.now());
    }

    @Test
    @DisplayName("Should create note successfully")
    void createNote_Success() {
        NoteRequest request = new NoteRequest("New Note", "Note Content");
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(noteRepository.save(any(Note.class))).thenReturn(testNote);

        NoteResponse response = noteService.createNote(request, userId);

        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("Test Title");
        verify(noteRepository).save(any(Note.class));
    }

    @Test
    @DisplayName("Should throw exception when creating note for non-existent user")
    void createNote_UserNotFound_ThrowsException() {
        NoteRequest request = new NoteRequest("New Note", "Note Content");
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.createNote(request, userId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(noteRepository, never()).save(any(Note.class));
    }

    @Test
    @DisplayName("Should get all notes for user")
    void getAllNotesForUser_Success() {
        Note note2 = new Note("Second Note", "Content 2", testUser);
        note2.setId(UUID.randomUUID());
        note2.setCreatedAt(Instant.now());
        note2.setUpdatedAt(Instant.now());

        when(noteRepository.findByUserId(userId)).thenReturn(Arrays.asList(testNote, note2));

        List<NoteResponse> notes = noteService.getAllNotesForUser(userId);

        assertThat(notes).hasSize(2);
        verify(noteRepository).findByUserId(userId);
    }

    @Test
    @DisplayName("Should return empty list when user has no notes")
    void getAllNotesForUser_NoNotes_ReturnsEmptyList() {
        when(noteRepository.findByUserId(userId)).thenReturn(List.of());

        List<NoteResponse> notes = noteService.getAllNotesForUser(userId);

        assertThat(notes).isEmpty();
    }

    @Test
    @DisplayName("Should get note by ID when user owns it")
    void getNoteById_Success() {
        when(noteRepository.findByIdAndUserId(noteId, userId)).thenReturn(Optional.of(testNote));

        NoteResponse response = noteService.getNoteById(noteId, userId);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(noteId);
    }

    @Test
    @DisplayName("Should throw exception when note not found")
    void getNoteById_NotFound_ThrowsException() {
        when(noteRepository.findByIdAndUserId(noteId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.getNoteById(noteId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should throw exception when user tries to access another user's note")
    void getNoteById_WrongUser_ThrowsException() {
        when(noteRepository.findByIdAndUserId(noteId, otherUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.getNoteById(noteId, otherUserId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should update note successfully when user owns it")
    void updateNote_Success() {
        NoteRequest request = new NoteRequest("Updated Title", "Updated Content");
        when(noteRepository.findByIdAndUserId(noteId, userId)).thenReturn(Optional.of(testNote));
        when(noteRepository.save(any(Note.class))).thenReturn(testNote);

        NoteResponse response = noteService.updateNote(noteId, request, userId);

        assertThat(response).isNotNull();
        verify(noteRepository).save(any(Note.class));
    }

    @Test
    @DisplayName("Should throw exception when updating note user doesn't own")
    void updateNote_WrongUser_ThrowsException() {
        NoteRequest request = new NoteRequest("Updated Title", "Updated Content");
        when(noteRepository.findByIdAndUserId(noteId, otherUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.updateNote(noteId, request, otherUserId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(noteRepository, never()).save(any(Note.class));
    }

    @Test
    @DisplayName("Should delete note successfully when user owns it")
    void deleteNote_Success() {
        when(noteRepository.existsByIdAndUserId(noteId, userId)).thenReturn(true);
        doNothing().when(noteRepository).deleteByIdAndUserId(noteId, userId);

        noteService.deleteNote(noteId, userId);

        verify(noteRepository).deleteByIdAndUserId(noteId, userId);
    }

    @Test
    @DisplayName("Should throw exception when deleting note user doesn't own")
    void deleteNote_WrongUser_ThrowsException() {
        when(noteRepository.existsByIdAndUserId(noteId, otherUserId)).thenReturn(false);

        assertThatThrownBy(() -> noteService.deleteNote(noteId, otherUserId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(noteRepository, never()).deleteByIdAndUserId(any(), any());
    }

    @Test
    @DisplayName("Should verify note ownership correctly")
    void isNoteOwnedByUser_ReturnsTrue() {
        when(noteRepository.existsByIdAndUserId(noteId, userId)).thenReturn(true);

        boolean isOwned = noteService.isNoteOwnedByUser(noteId, userId);

        assertThat(isOwned).isTrue();
    }

    @Test
    @DisplayName("Should return false when note not owned by user")
    void isNoteOwnedByUser_ReturnsFalse() {
        when(noteRepository.existsByIdAndUserId(noteId, otherUserId)).thenReturn(false);

        boolean isOwned = noteService.isNoteOwnedByUser(noteId, otherUserId);

        assertThat(isOwned).isFalse();
    }
}

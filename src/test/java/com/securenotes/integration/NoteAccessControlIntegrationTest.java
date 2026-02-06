package com.securenotes.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.securenotes.dto.AuthResponse;
import com.securenotes.dto.LoginRequest;
import com.securenotes.dto.NoteRequest;
import com.securenotes.dto.NoteResponse;
import com.securenotes.dto.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Note access control.
 * Tests the critical requirement: User A must NEVER access User B's notes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class NoteAccessControlIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("securenotes_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("jwt.secret", () -> "dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdG9rZW4tZ2VuZXJhdGlvbi1tdXN0LWJlLWxvbmc=");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String userAToken;
    private String userBToken;
    private String userANoteId;

    @BeforeEach
    void setUp() throws Exception {
        long timestamp = System.currentTimeMillis();
        
        RegisterRequest userARegister = new RegisterRequest(
                "userA" + timestamp,
                "userA" + timestamp + "@example.com",
                "password123"
        );
        RegisterRequest userBRegister = new RegisterRequest(
                "userB" + timestamp,
                "userB" + timestamp + "@example.com",
                "password123"
        );

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userARegister)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userBRegister)))
                .andExpect(status().isCreated());

        MvcResult userALogin = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(userARegister.getEmail(), "password123"))))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult userBLogin = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(userBRegister.getEmail(), "password123"))))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse userAAuth = objectMapper.readValue(
                userALogin.getResponse().getContentAsString(), AuthResponse.class);
        AuthResponse userBAuth = objectMapper.readValue(
                userBLogin.getResponse().getContentAsString(), AuthResponse.class);

        userAToken = userAAuth.getAccessToken();
        userBToken = userBAuth.getAccessToken();

        NoteRequest noteRequest = new NoteRequest("User A's Secret Note", "This is private content");
        MvcResult noteResult = mockMvc.perform(post("/api/notes")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noteRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        NoteResponse noteResponse = objectMapper.readValue(
                noteResult.getResponse().getContentAsString(), NoteResponse.class);
        userANoteId = noteResponse.getId().toString();
    }

    @Test
    @DisplayName("User A can access their own note")
    void userA_CanAccessOwnNote() throws Exception {
        mockMvc.perform(get("/api/notes/" + userANoteId)
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("User A's Secret Note"));
    }

    @Test
    @DisplayName("User B CANNOT access User A's note - returns 404")
    void userB_CannotAccessUserANote_Returns404() throws Exception {
        mockMvc.perform(get("/api/notes/" + userANoteId)
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("User B CANNOT update User A's note - returns 404")
    void userB_CannotUpdateUserANote_Returns404() throws Exception {
        NoteRequest updateRequest = new NoteRequest("Hacked Title", "Hacked Content");

        mockMvc.perform(put("/api/notes/" + userANoteId)
                        .header("Authorization", "Bearer " + userBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("User B CANNOT delete User A's note - returns 404")
    void userB_CannotDeleteUserANote_Returns404() throws Exception {
        mockMvc.perform(delete("/api/notes/" + userANoteId)
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("User B's note list does not contain User A's notes")
    void userB_NoteList_DoesNotContainUserANotes() throws Exception {
        mockMvc.perform(get("/api/notes")
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("User A can update their own note")
    void userA_CanUpdateOwnNote() throws Exception {
        NoteRequest updateRequest = new NoteRequest("Updated Title", "Updated Content");

        mockMvc.perform(put("/api/notes/" + userANoteId)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Title"));
    }

    @Test
    @DisplayName("User A can delete their own note")
    void userA_CanDeleteOwnNote() throws Exception {
        NoteRequest newNote = new NoteRequest("Note to Delete", "Content");
        MvcResult result = mockMvc.perform(post("/api/notes")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newNote)))
                .andExpect(status().isCreated())
                .andReturn();

        NoteResponse noteResponse = objectMapper.readValue(
                result.getResponse().getContentAsString(), NoteResponse.class);

        mockMvc.perform(delete("/api/notes/" + noteResponse.getId())
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/notes/" + noteResponse.getId())
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Unauthenticated request returns 401")
    void unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/api/notes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Invalid token returns 401")
    void invalidToken_Returns401() throws Exception {
        mockMvc.perform(get("/api/notes")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Validation error returns 400 with field errors")
    void validation_InvalidNote_Returns400() throws Exception {
        NoteRequest invalidRequest = new NoteRequest("", "Content");

        mockMvc.perform(post("/api/notes")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }
}

package com.securenotes.controller;

import com.securenotes.entity.Role;
import com.securenotes.entity.User;
import com.securenotes.repository.RoleRepository;
import com.securenotes.repository.UserRepository;
import com.securenotes.security.JwtService;
import com.securenotes.security.UserDetailsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashSet;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for AdminController.
 * Verifies that ROLE_USER cannot access admin endpoints (403 Forbidden).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AdminControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("securenotes_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private User regularUser;
    private User adminUser;
    private String userToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        Role roleUser = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_USER")));
        Role roleAdmin = roleRepository.findByName("ROLE_ADMIN")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_ADMIN")));

        if (userRepository.findByEmail("testuser@test.com").isEmpty()) {
            regularUser = new User("testuser", "testuser@test.com", passwordEncoder.encode("password123"));
            Set<Role> userRoles = new HashSet<>();
            userRoles.add(roleUser);
            regularUser.setRoles(userRoles);
            regularUser = userRepository.save(regularUser);
        } else {
            regularUser = userRepository.findByEmail("testuser@test.com").get();
        }

        if (userRepository.findByEmail("testadmin@test.com").isEmpty()) {
            adminUser = new User("testadmin", "testadmin@test.com", passwordEncoder.encode("password123"));
            Set<Role> adminRoles = new HashSet<>();
            adminRoles.add(roleAdmin);
            adminUser.setRoles(adminRoles);
            adminUser = userRepository.save(adminUser);
        } else {
            adminUser = userRepository.findByEmail("testadmin@test.com").get();
        }

        UserDetailsImpl userDetails = UserDetailsImpl.build(regularUser);
        userToken = jwtService.generateAccessToken(userDetails, regularUser.getId());

        UserDetailsImpl adminDetails = UserDetailsImpl.build(adminUser);
        adminToken = jwtService.generateAccessToken(adminDetails, adminUser.getId());
    }

    @Test
    @DisplayName("ROLE_USER should receive 403 Forbidden when accessing admin dashboard")
    void whenUserAccessesAdminDashboard_thenForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ROLE_ADMIN should successfully access admin dashboard")
    void whenAdminAccessesAdminDashboard_thenSuccess() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Welcome to Admin Dashboard"))
                .andExpect(jsonPath("$.adminEmail").value("testadmin@test.com"));
    }

    @Test
    @DisplayName("Unauthenticated request should receive 401 Unauthorized")
    void whenUnauthenticatedAccessesAdminDashboard_thenUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isUnauthorized());
    }
}

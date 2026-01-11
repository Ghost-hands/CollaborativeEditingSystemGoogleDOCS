package com.collaborative.editing.user.controller;

import com.collaborative.editing.common.dto.UserDTO;
import com.collaborative.editing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InternalUserController.class)
@DisplayName("Internal User Controller Tests")
class InternalUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    private UserDTO testUser;

    @BeforeEach
    void setUp() {
        testUser = new UserDTO();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setActive(true);
    }

    @Test
    @DisplayName("Get user by ID internal - success")
    void testGetUserByIdInternal_Success() throws Exception {
        when(userService.getUserById(1L)).thenReturn(testUser);

        mockMvc.perform(get("/internal/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.username").value("testuser"));
    }

    @Test
    @DisplayName("Get user by ID internal - not found")
    void testGetUserByIdInternal_NotFound() throws Exception {
        when(userService.getUserById(999L))
            .thenThrow(new RuntimeException("User not found"));

        mockMvc.perform(get("/internal/users/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("User not found"));
    }

    @Test
    @DisplayName("User exists - true and active")
    void testUserExists_TrueAndActive() throws Exception {
        when(userService.getUserById(1L)).thenReturn(testUser);

        mockMvc.perform(get("/internal/users/1/exists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(true))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("User exists - true but inactive")
    void testUserExists_TrueButInactive() throws Exception {
        testUser.setActive(false);
        when(userService.getUserById(1L)).thenReturn(testUser);

        mockMvc.perform(get("/internal/users/1/exists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(true))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("User exists - false")
    void testUserExists_False() throws Exception {
        when(userService.getUserById(999L))
            .thenThrow(new RuntimeException("User not found"));

        mockMvc.perform(get("/internal/users/999/exists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(false))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("Get users by IDs batch - success")
    void testGetUsersByIds_Success() throws Exception {
        UserDTO user2 = new UserDTO();
        user2.setId(2L);
        user2.setUsername("user2");

        when(userService.getUsersByIds(Arrays.asList(1L, 2L)))
            .thenReturn(Arrays.asList(testUser, user2));

        mockMvc.perform(get("/internal/users/batch")
                .param("ids", "1", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[1].id").value(2L));
    }

    @Test
    @DisplayName("Get users by IDs batch - empty list")
    void testGetUsersByIds_EmptyList() throws Exception {
        when(userService.getUsersByIds(anyList())).thenReturn(Arrays.asList());

        mockMvc.perform(get("/internal/users/batch")
                .param("ids", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("Is admin - true")
    void testIsAdmin_True() throws Exception {
        when(userService.isAdmin(1L)).thenReturn(true);

        mockMvc.perform(get("/internal/users/1/isAdmin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAdmin").value(true));
    }

    @Test
    @DisplayName("Is admin - false")
    void testIsAdmin_False() throws Exception {
        when(userService.isAdmin(1L)).thenReturn(false);

        mockMvc.perform(get("/internal/users/1/isAdmin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAdmin").value(false));
    }

    @Test
    @DisplayName("Is admin - user not found")
    void testIsAdmin_UserNotFound() throws Exception {
        when(userService.isAdmin(999L))
            .thenThrow(new RuntimeException("User not found"));

        mockMvc.perform(get("/internal/users/999/isAdmin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAdmin").value(false))
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("Health check")
    void testHealth() throws Exception {
        mockMvc.perform(get("/internal/users/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("user-management-service"));
    }
}

package com.collaborative.editing.document.client;

import com.collaborative.editing.common.dto.UserDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("User Service Client Tests")
class UserServiceClientTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private UserServiceClient userServiceClient;

    private String userServiceUrl = "http://localhost:8081";
    private Long userId;
    private UserDTO testUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(userServiceClient, "userServiceUrl", userServiceUrl);
        userId = 1L;
        testUser = new UserDTO();
        testUser.setId(userId);
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
    }

    @Test
    @DisplayName("Get user by ID successfully")
    void testGetUserById_Success() {
        ResponseEntity<UserDTO> response = new ResponseEntity<>(testUser, HttpStatus.OK);
        when(restTemplate.getForEntity(anyString(), eq(UserDTO.class))).thenReturn(response);

        UserDTO result = userServiceClient.getUserById(userId);

        assertNotNull(result);
        assertEquals(userId, result.getId());
        assertEquals("testuser", result.getUsername());
        verify(restTemplate, times(1)).getForEntity(
                eq(userServiceUrl + "/internal/users/" + userId), 
                eq(UserDTO.class));
    }

    @Test
    @DisplayName("Get user by ID - not found")
    void testGetUserById_NotFound() {
        ResponseEntity<UserDTO> response = new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
        when(restTemplate.getForEntity(anyString(), eq(UserDTO.class))).thenReturn(response);

        UserDTO result = userServiceClient.getUserById(userId);

        assertNull(result);
    }

    @Test
    @DisplayName("Get user by ID - service error")
    void testGetUserById_ServiceError() {
        when(restTemplate.getForEntity(anyString(), eq(UserDTO.class)))
                .thenThrow(new RestClientException("Service unavailable"));

        UserDTO result = userServiceClient.getUserById(userId);

        assertNull(result);
    }

    @Test
    @DisplayName("Check if user exists - exists and active")
    void testUserExists_ExistsAndActive() {
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("exists", true);
        responseBody.put("active", true);
        ResponseEntity<Map<String, Object>> response = new ResponseEntity<>(responseBody, HttpStatus.OK);
        
        when(restTemplate.getForEntity(anyString(), any(Class.class))).thenReturn(response);

        boolean result = userServiceClient.userExists(userId);

        assertTrue(result);
    }

    @Test
    @DisplayName("Check if user exists - exists but inactive")
    void testUserExists_ExistsButInactive() {
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("exists", true);
        responseBody.put("active", false);
        ResponseEntity<Map<String, Object>> response = new ResponseEntity<>(responseBody, HttpStatus.OK);
        
        when(restTemplate.getForEntity(anyString(), any(Class.class))).thenReturn(response);

        boolean result = userServiceClient.userExists(userId);

        assertFalse(result);
    }

    @Test
    @DisplayName("Check if user exists - does not exist")
    void testUserExists_DoesNotExist() {
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("exists", false);
        responseBody.put("active", false);
        ResponseEntity<Map<String, Object>> response = new ResponseEntity<>(responseBody, HttpStatus.OK);
        
        when(restTemplate.getForEntity(anyString(), any(Class.class))).thenReturn(response);

        boolean result = userServiceClient.userExists(userId);

        assertFalse(result);
    }

    @Test
    @DisplayName("Check if user exists - service error")
    void testUserExists_ServiceError() {
        when(restTemplate.getForEntity(anyString(), any(Class.class)))
                .thenThrow(new RestClientException("Service unavailable"));

        boolean result = userServiceClient.userExists(userId);

        assertFalse(result);
    }

    @Test
    @DisplayName("Get multiple users by IDs - success")
    void testGetUsersByIds_Success() {
        List<Map<String, Object>> responseBody = List.of(
                Map.of("id", "1", "username", "user1", "email", "user1@example.com"),
                Map.of("id", "2", "username", "user2", "email", "user2@example.com")
        );
        ResponseEntity<List> response = new ResponseEntity<>(responseBody, HttpStatus.OK);
        
        when(restTemplate.getForEntity(anyString(), eq(List.class))).thenReturn(response);

        List<UserDTO> result = userServiceClient.getUsersByIds(List.of(1L, 2L));

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals("user1", result.get(0).getUsername());
    }

    @Test
    @DisplayName("Get multiple users by IDs - empty list")
    void testGetUsersByIds_EmptyList() {
        ResponseEntity<List> response = new ResponseEntity<>(List.of(), HttpStatus.OK);
        
        when(restTemplate.getForEntity(anyString(), eq(List.class))).thenReturn(response);

        List<UserDTO> result = userServiceClient.getUsersByIds(List.of(1L, 2L));

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Get multiple users by IDs - service error")
    void testGetUsersByIds_ServiceError() {
        when(restTemplate.getForEntity(anyString(), eq(List.class)))
                .thenThrow(new RestClientException("Service unavailable"));

        List<UserDTO> result = userServiceClient.getUsersByIds(List.of(1L, 2L));

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Check if user is admin - is admin")
    void testIsAdmin_IsAdmin() {
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("isAdmin", true);
        ResponseEntity<Map<String, Object>> response = new ResponseEntity<>(responseBody, HttpStatus.OK);
        
        when(restTemplate.getForEntity(anyString(), any(Class.class))).thenReturn(response);

        boolean result = userServiceClient.isAdmin(userId);

        assertTrue(result);
    }

    @Test
    @DisplayName("Check if user is admin - is not admin")
    void testIsAdmin_IsNotAdmin() {
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("isAdmin", false);
        ResponseEntity<Map<String, Object>> response = new ResponseEntity<>(responseBody, HttpStatus.OK);
        
        when(restTemplate.getForEntity(anyString(), any(Class.class))).thenReturn(response);

        boolean result = userServiceClient.isAdmin(userId);

        assertFalse(result);
    }

    @Test
    @DisplayName("Check if user is admin - service error")
    void testIsAdmin_ServiceError() {
        when(restTemplate.getForEntity(anyString(), any(Class.class)))
                .thenThrow(new RestClientException("Service unavailable"));

        boolean result = userServiceClient.isAdmin(userId);

        assertFalse(result);
    }
}

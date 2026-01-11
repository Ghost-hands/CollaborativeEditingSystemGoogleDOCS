package com.collaborative.editing.user.service;

import com.collaborative.editing.common.dto.UserDTO;
import com.collaborative.editing.user.model.User;
import com.collaborative.editing.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("User Service Enhanced Tests")
@SuppressWarnings("null")
class UserServiceEnhancedTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private com.collaborative.editing.user.client.DocumentServiceClient documentServiceClient;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private UserDTO testUserDTO;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setPassword("encodedPassword");
        testUser.setFirstName("Test");
        testUser.setLastName("User");
        testUser.setCreatedAt(LocalDateTime.now());
        testUser.setActive(true);

        testUserDTO = new UserDTO();
        testUserDTO.setUsername("testuser");
        testUserDTO.setEmail("test@example.com");
        testUserDTO.setPassword("password123");
        testUserDTO.setFirstName("Test");
        testUserDTO.setLastName("User");
    }

    @Test
    @DisplayName("Register user with all fields")
    void testRegisterUser_AllFields() {
        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        UserDTO result = userService.registerUser(testUserDTO);

        assertNotNull(result);
        assertEquals("testuser", result.getUsername());
        assertEquals("test@example.com", result.getEmail());
        assertEquals("Test", result.getFirstName());
        assertEquals("User", result.getLastName());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Register user with minimal fields")
    void testRegisterUser_MinimalFields() {
        UserDTO minimalDTO = new UserDTO();
        minimalDTO.setUsername("minimal");
        minimalDTO.setEmail("minimal@example.com");
        minimalDTO.setPassword("pass123");

        User minimalUser = new User();
        minimalUser.setId(2L);
        minimalUser.setUsername("minimal");
        minimalUser.setEmail("minimal@example.com");
        minimalUser.setPassword("encoded");
        minimalUser.setActive(true);

        when(userRepository.existsByUsername("minimal")).thenReturn(false);
        when(userRepository.existsByEmail("minimal@example.com")).thenReturn(false);
        when(passwordEncoder.encode("pass123")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenReturn(minimalUser);

        UserDTO result = userService.registerUser(minimalDTO);

        assertNotNull(result);
        assertEquals("minimal", result.getUsername());
    }

    @Test
    @DisplayName("Register user - email already exists")
    void testRegisterUser_EmailExists() {
        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThrows(RuntimeException.class, () -> {
            userService.registerUser(testUserDTO);
        });
    }

    @Test
    @DisplayName("Register user - empty username")
    void testRegisterUser_EmptyUsername() {
        testUserDTO.setUsername("");

        assertThrows(RuntimeException.class, () -> {
            userService.registerUser(testUserDTO);
        });
    }

    @Test
    @DisplayName("Register user - null email")
    void testRegisterUser_NullEmail() {
        testUserDTO.setEmail(null);

        assertThrows(RuntimeException.class, () -> {
            userService.registerUser(testUserDTO);
        });
    }

    @Test
    @DisplayName("Register user - empty password")
    void testRegisterUser_EmptyPassword() {
        testUserDTO.setPassword("");

        assertThrows(RuntimeException.class, () -> {
            userService.registerUser(testUserDTO);
        });
    }

    @Test
    @DisplayName("Authenticate user - inactive account")
    void testAuthenticateUser_Inactive() {
        testUser.setActive(false);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password123", "encodedPassword")).thenReturn(true);

        assertThrows(RuntimeException.class, () -> {
            userService.authenticateUser("testuser", "password123");
        });
    }

    @Test
    @DisplayName("Authenticate user - user not found")
    void testAuthenticateUser_NotFound() {
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> {
            userService.authenticateUser("nonexistent", "password");
        });
    }

    @Test
    @DisplayName("Get user by ID - not found")
    void testGetUserById_NotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> {
            userService.getUserById(999L);
        });
    }

    @Test
    @DisplayName("Update user profile - partial update")
    void testUpdateUserProfile_PartialUpdate() {
        UserDTO updateDTO = new UserDTO();
        updateDTO.setFirstName("NewFirst");
        // lastName not set

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        UserDTO result = userService.updateUserProfile(1L, updateDTO);

        assertNotNull(result);
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Update user profile - user not found")
    void testUpdateUserProfile_NotFound() {
        UserDTO updateDTO = new UserDTO();
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> {
            userService.updateUserProfile(999L, updateDTO);
        });
    }

    @Test
    @DisplayName("Delete user - sets active to false")
    void testDeleteUser_SetsInactive() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        userService.deleteUser(1L);

        assertFalse(testUser.getActive());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Get all users")
    void testGetAllUsers() {
        User user2 = new User();
        user2.setId(2L);
        user2.setUsername("user2");
        user2.setEmail("user2@example.com");
        user2.setActive(true);

        when(userRepository.findAll()).thenReturn(Arrays.asList(testUser, user2));

        List<UserDTO> result = userService.getAllUsers();

        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("Get users by IDs - batch operation")
    void testGetUsersByIds() {
        User user2 = new User();
        user2.setId(2L);
        user2.setUsername("user2");
        user2.setEmail("user2@example.com");
        user2.setActive(true);

        when(userRepository.findAllById(Arrays.asList(1L, 2L)))
            .thenReturn(Arrays.asList(testUser, user2));

        List<UserDTO> result = userService.getUsersByIds(Arrays.asList(1L, 2L));

        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("Get user by ID - active user exists")
    void testGetUserById_ActiveUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        UserDTO result = userService.getUserById(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
    }

    @Test
    @DisplayName("Get user by ID - inactive user")
    void testGetUserById_InactiveUser() {
        testUser.setActive(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        UserDTO result = userService.getUserById(1L);

        assertNotNull(result);
        assertFalse(testUser.getActive());
    }

    @Test
    @DisplayName("Register user - trims whitespace")
    void testRegisterUser_TrimsWhitespace() {
        testUserDTO.setUsername("  testuser  ");
        testUserDTO.setEmail("  test@example.com  ");
        testUserDTO.setFirstName("  Test  ");

        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        UserDTO result = userService.registerUser(testUserDTO);

        assertNotNull(result);
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Get active users only")
    void testGetActiveUsers() {
        User activeUser2 = new User();
        activeUser2.setId(2L);
        activeUser2.setUsername("activeuser");
        activeUser2.setEmail("active@example.com");
        activeUser2.setActive(true);

        User inactiveUser = new User();
        inactiveUser.setId(3L);
        inactiveUser.setUsername("inactiveuser");
        inactiveUser.setEmail("inactive@example.com");
        inactiveUser.setActive(false);

        when(userRepository.findByActiveTrue())
            .thenReturn(Arrays.asList(testUser, activeUser2));

        List<UserDTO> result = userService.getActiveUsers();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(UserDTO::getActive));
    }

    @Test
    @DisplayName("Get active users - empty list")
    void testGetActiveUsers_Empty() {
        when(userRepository.findByActiveTrue())
            .thenReturn(Collections.emptyList());

        List<UserDTO> result = userService.getActiveUsers();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Is admin - true")
    void testIsAdmin_True() {
        testUser.setIsAdmin(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        boolean result = userService.isAdmin(1L);

        assertTrue(result);
    }

    @Test
    @DisplayName("Is admin - false")
    void testIsAdmin_False() {
        testUser.setIsAdmin(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        boolean result = userService.isAdmin(1L);

        assertFalse(result);
    }

    @Test
    @DisplayName("Is admin - null userId")
    void testIsAdmin_NullUserId() {
        boolean result = userService.isAdmin(null);

        assertFalse(result);
    }

    @Test
    @DisplayName("Is admin - user not found")
    void testIsAdmin_UserNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        boolean result = userService.isAdmin(999L);

        assertFalse(result);
    }

    @Test
    @DisplayName("Is admin - exception handling")
    void testIsAdmin_Exception() {
        when(userRepository.findById(1L))
            .thenThrow(new RuntimeException("Database error"));

        boolean result = userService.isAdmin(1L);

        assertFalse(result);
    }

    @Test
    @DisplayName("Delete user - hard delete with documents")
    void testDeleteUser_HardDelete() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(documentServiceClient.deleteAllDocumentsByOwner(1L)).thenReturn(true);

        userService.deleteUser(1L);

        verify(userRepository, times(1)).deleteById(1L);
        verify(documentServiceClient, times(1)).deleteAllDocumentsByOwner(1L);
    }

    @Test
    @DisplayName("Delete user - document deletion fails but continues")
    void testDeleteUser_DocumentDeletionFails() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(documentServiceClient.deleteAllDocumentsByOwner(1L))
            .thenThrow(new RuntimeException("Service unavailable"));

        userService.deleteUser(1L);

        // Should still delete user even if document deletion fails
        verify(userRepository, times(1)).deleteById(1L);
    }

    @Test
    @DisplayName("Delete user - null ID")
    void testDeleteUser_NullId() {
        assertThrows(RuntimeException.class, () -> {
            userService.deleteUser(null);
        });
    }

    @Test
    @DisplayName("Delete user - user not found")
    void testDeleteUser_NotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> {
            userService.deleteUser(999L);
        });
    }
}


package com.collaborative.editing.user.service;

import com.collaborative.editing.common.dto.UserDTO;
import com.collaborative.editing.user.client.DocumentServiceClient;
import com.collaborative.editing.user.model.User;
import com.collaborative.editing.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class UserService {
    
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private DocumentServiceClient documentServiceClient;

    public UserDTO registerUser(UserDTO userDTO) {
        logger.debug("Registering new user: username={}, email={}", userDTO.getUsername(), userDTO.getEmail());
        
        // Validate required fields
        if (userDTO.getUsername() == null || userDTO.getUsername().trim().isEmpty()) {
            logger.warn("Registration failed: Username is required");
            throw new RuntimeException("Username is required");
        }
        if (userDTO.getEmail() == null || userDTO.getEmail().trim().isEmpty()) {
            logger.warn("Registration failed: Email is required");
            throw new RuntimeException("Email is required");
        }
        if (userDTO.getPassword() == null || userDTO.getPassword().trim().isEmpty()) {
            logger.warn("Registration failed: Password is required");
            throw new RuntimeException("Password is required");
        }
        
        if (userRepository.existsByUsername(userDTO.getUsername())) {
            logger.warn("Registration failed: Username already exists - {}", userDTO.getUsername());
            throw new RuntimeException("Username already exists");
        }
        if (userRepository.existsByEmail(userDTO.getEmail())) {
            logger.warn("Registration failed: Email already exists - {}", userDTO.getEmail());
            throw new RuntimeException("Email already exists");
        }
        
        try {
            User user = new User();
            user.setUsername(userDTO.getUsername().trim());
            user.setEmail(userDTO.getEmail().trim());
            user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
            if (userDTO.getFirstName() != null) {
                user.setFirstName(userDTO.getFirstName().trim());
            }
            if (userDTO.getLastName() != null) {
                user.setLastName(userDTO.getLastName().trim());
            }
            user.setProfilePicture(userDTO.getProfilePicture());
            user.setCreatedAt(LocalDateTime.now());
            user.setActive(true);
            user.setIsAdmin(userDTO.getIsAdmin() != null ? userDTO.getIsAdmin() : false);
            
            User savedUser = userRepository.save(user);
            logger.info("User registered successfully: ID={}, username={}", savedUser.getId(), savedUser.getUsername());
            return convertToDTO(savedUser);
        } catch (Exception e) {
            logger.error("Error saving user: username={}, email={}", userDTO.getUsername(), userDTO.getEmail(), e);
            throw new RuntimeException("Failed to register user: " + e.getMessage(), e);
        }
    }

    public UserDTO authenticateUser(String username, String password) {
        logger.debug("Authenticating user: username={}", username);
        
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    logger.warn("Authentication failed: User not found - {}", username);
                    return new RuntimeException("User not found");
                });
        
        if (!passwordEncoder.matches(password, user.getPassword())) {
            logger.warn("Authentication failed: Invalid password for user - {}", username);
            throw new RuntimeException("Invalid password");
        }
        
        if (!user.getActive()) {
            logger.warn("Authentication failed: User account is inactive - {}", username);
            throw new RuntimeException("User account is inactive");
        }
        
        logger.info("User authenticated successfully: ID={}, username={}", user.getId(), user.getUsername());
        return convertToDTO(user);
    }
    

    public UserDTO getUserById(Long id) {
        logger.debug("Retrieving user by ID: {}", id);
        if (id == null) {
            logger.warn("Get user failed: User ID is null");
            throw new RuntimeException("User ID cannot be null");
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> {
                    logger.warn("User not found: ID={}", id);
                    return new RuntimeException("User not found");
                });
        logger.debug("User retrieved: ID={}, username={}", id, user.getUsername());
        return convertToDTO(user);
    }

    public UserDTO updateUserProfile(Long id, UserDTO userDTO) {
        logger.debug("Updating user profile: ID={}", id);
        if (id == null) {
            logger.warn("Profile update failed: User ID is null");
            throw new RuntimeException("User ID cannot be null");
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> {
                    logger.warn("Profile update failed: User not found - ID={}", id);
                    return new RuntimeException("User not found");
                });
        
        boolean emailChanged = false;
        boolean usernameChanged = false;
        if (userDTO.getFirstName() != null) {
            user.setFirstName(userDTO.getFirstName());
            logger.debug("Updated firstName for user ID={}", id);
        }
        if (userDTO.getLastName() != null) {
            user.setLastName(userDTO.getLastName());
            logger.debug("Updated lastName for user ID={}", id);
        }
        if (userDTO.getUsername() != null && !userDTO.getUsername().trim().isEmpty() && !userDTO.getUsername().equals(user.getUsername())) {
            if (userRepository.existsByUsername(userDTO.getUsername().trim())) {
                logger.warn("Profile update failed: Username already exists - {}", userDTO.getUsername());
                throw new RuntimeException("Username already exists");
            }
            user.setUsername(userDTO.getUsername().trim());
            usernameChanged = true;
            logger.debug("Updated username for user ID={}", id);
        }
        if (userDTO.getEmail() != null && !userDTO.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(userDTO.getEmail())) {
                logger.warn("Profile update failed: Email already exists - {}", userDTO.getEmail());
                throw new RuntimeException("Email already exists");
            }
            user.setEmail(userDTO.getEmail());
            emailChanged = true;
            logger.debug("Updated email for user ID={}", id);
        }
        if (userDTO.getProfilePicture() != null) {
            user.setProfilePicture(userDTO.getProfilePicture());
            logger.debug("Updated profilePicture for user ID={}", id);
        }
        if (userDTO.getPassword() != null && !userDTO.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
            logger.debug("Updated password for user ID={}", id);
        }
        
        user.setUpdatedAt(LocalDateTime.now());
        User updatedUser = userRepository.save(user);
        logger.info("User profile updated successfully: ID={}, username={}, usernameChanged={}, emailChanged={}", 
                id, updatedUser.getUsername(), usernameChanged, emailChanged);
        return convertToDTO(updatedUser);
    }

    public List<UserDTO> getAllUsers() {
        logger.debug("Retrieving all users");
        List<UserDTO> users = userRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        logger.debug("Retrieved {} users", users.size());
        return users;
    }

    public List<UserDTO> getActiveUsers() {
        logger.debug("Retrieving active users only");
        List<UserDTO> users = userRepository.findByActiveTrue().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        logger.debug("Retrieved {} active users", users.size());
        return users;
    }

    @SuppressWarnings("null")
    public List<UserDTO> getUsersByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        // Filter out null values to avoid null type safety issues
        List<Long> validIds = ids.stream()
                .filter(id -> id != null)
                .collect(Collectors.toList());
        return userRepository.findAllById(validIds).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public void deleteUser(Long id) {
        logger.debug("Deleting user: ID={}", id);
        if (id == null) {
            logger.warn("Delete user failed: User ID is null");
            throw new RuntimeException("User ID cannot be null");
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> {
                    logger.warn("Delete user failed: User not found - ID={}", id);
                    return new RuntimeException("User not found");
                });
        
        // Delete all documents owned by this user before deleting the account
        try {
            logger.info("Deleting all documents owned by user: ID={}, username={}", id, user.getUsername());
            boolean documentsDeleted = documentServiceClient.deleteAllDocumentsByOwner(id);
            if (documentsDeleted) {
                logger.info("Successfully deleted all documents for user: ID={}", id);
            } else {
                logger.warn("Failed to delete some or all documents for user: ID={}", id);
                // Continue with user deletion even if document deletion fails
            }
        } catch (Exception e) {
            logger.error("Error deleting documents for user: ID={}", id, e);
            // Continue with user deletion even if document deletion fails
        }
        
        // Hard delete the user from the database
        String username = user.getUsername(); // Store username before deletion for logging
        userRepository.deleteById(id);
        logger.info("User hard-deleted from database: ID={}, username={}", id, username);
    }

    private UserDTO convertToDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setActive(user.getActive());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setProfilePicture(user.getProfilePicture());
        dto.setIsAdmin(user.getIsAdmin());
        return dto;
    }
    
    /**
     * Check if user is admin
     */
    public boolean isAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            return user.getIsAdmin() != null && user.getIsAdmin();
        } catch (Exception e) {
            logger.warn("Error checking admin status for userId={}: {}", userId, e.getMessage());
            return false;
        }
    }
}


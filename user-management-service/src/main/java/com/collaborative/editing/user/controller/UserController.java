package com.collaborative.editing.user.controller;

import com.collaborative.editing.common.dto.AuthRequest;
import com.collaborative.editing.common.dto.UserDTO;
import com.collaborative.editing.user.service.UserService;
import com.collaborative.editing.user.util.JwtUtil;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/users")
public class UserController {
    
    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody UserDTO userDTO) {
        logger.info("Registration request received for username: {}", userDTO.getUsername());
        try {
            UserDTO registeredUser = userService.registerUser(userDTO);
            logger.info("User registered successfully with ID: {}, username: {}", registeredUser.getId(), registeredUser.getUsername());
            return ResponseEntity.status(HttpStatus.CREATED).body(registeredUser);
        } catch (RuntimeException e) {
            logger.warn("Registration failed for username: {} - {}", userDTO.getUsername(), e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        } catch (Exception e) {
            logger.error("Unexpected error during registration for username: {}", userDTO.getUsername(), e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Registration failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @PostMapping("/authenticate")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody AuthRequest authRequest) {
        logger.info("Authentication request received for username: {}", authRequest.getUsername());
        try {
            UserDTO user = userService.authenticateUser(
                authRequest.getUsername(), 
                authRequest.getPassword()
            );
            
            // Generate JWT token (5 minutes expiration)
            String token = jwtUtil.generateToken(user.getId(), user.getUsername());
            logger.info("User authenticated successfully: ID={}, username={}, token generated", user.getId(), user.getUsername());
            
            // Return user data with token
            Map<String, Object> response = new HashMap<>();
            response.put("user", user);
            response.put("token", token);
            response.put("expiresIn", 300000); // 5 minutes in milliseconds
            
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            logger.warn("Authentication failed for username: {} - {}", authRequest.getUsername(), e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }
    }

    @GetMapping("/{id:\\d+}")
    public ResponseEntity<?> getUserById(@PathVariable Long id) {
        logger.debug("Get user request received for ID: {}", id);
        try {
            UserDTO user = userService.getUserById(id);
            logger.debug("User retrieved successfully: ID={}, username={}", id, user.getUsername());
            return ResponseEntity.ok(user);
        } catch (RuntimeException e) {
            logger.warn("User not found: ID={} - {}", id, e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }

    @PutMapping("/{id:\\d+}/profile")
    public ResponseEntity<?> updateUserProfile(@PathVariable Long id, @RequestBody UserDTO userDTO) {
        logger.info("Update profile request received for user ID: {}", id);
        try {
            UserDTO updatedUser = userService.updateUserProfile(id, userDTO);
            logger.info("User profile updated successfully: ID={}, username={}", id, updatedUser.getUsername());
            return ResponseEntity.ok(updatedUser);
        } catch (RuntimeException e) {
            logger.warn("Profile update failed for user ID: {} - {}", id, e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    @GetMapping
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        logger.debug("Get all users request received");
        List<UserDTO> users = userService.getAllUsers();
        logger.debug("Retrieved {} users", users.size());
        return ResponseEntity.ok(users);
    }

    @DeleteMapping("/{id:\\d+}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id, @RequestParam(required = false) Long adminId) {
        logger.info("Delete user request received for ID: {}, adminId: {}", id, adminId);
        try {
            // Check if adminId is provided and is admin
            if (adminId != null && !userService.isAdmin(adminId)) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Only admins can delete other users");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
            }
            
            // If adminId is provided, it's an admin deleting another user
            // If adminId is null, it's a user deleting their own account
            if (adminId != null && !adminId.equals(id)) {
                // Admin deleting another user
                userService.deleteUser(id);
            } else if (adminId == null || adminId.equals(id)) {
                // User deleting their own account
                userService.deleteUser(id);
            } else {
                throw new RuntimeException("Invalid operation");
            }
            
            logger.info("User deleted successfully: ID={}", id);
            Map<String, String> response = new HashMap<>();
            response.put("message", "User deleted successfully");
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            logger.warn("User deletion failed: ID={} - {}", id, e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }
    
    /**
     * Admin endpoint to get all users (for admin panel)
     * Returns only active users - deleted/inactive users are excluded
     */
    @GetMapping("/admin/all")
    public ResponseEntity<?> getAllUsersForAdmin(@RequestParam Long adminId) {
        logger.info("Admin get all users request from adminId: {}", adminId);
        try {
            if (!userService.isAdmin(adminId)) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Only admins can access this endpoint");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
            }
            // Return only active users - deleted users are excluded from admin panel
            List<UserDTO> users = userService.getActiveUsers();
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            logger.error("Error getting users for admin: {}", e.getMessage(), e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to retrieve users");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    
    /**
     * Admin endpoint to delete a user
     */
    @DeleteMapping("/admin/{id:\\d+}")
    public ResponseEntity<?> adminDeleteUser(@PathVariable Long id, @RequestParam Long adminId) {
        logger.info("Admin delete user request: userId={}, adminId={}", id, adminId);
        try {
            if (!userService.isAdmin(adminId)) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Only admins can delete users");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
            }
            
            if (adminId.equals(id)) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Admins cannot delete themselves");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }
            
            userService.deleteUser(id);
            Map<String, String> response = new HashMap<>();
            response.put("message", "User deleted successfully");
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            logger.warn("Admin user deletion failed: userId={}, adminId={} - {}", id, adminId, e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }
}


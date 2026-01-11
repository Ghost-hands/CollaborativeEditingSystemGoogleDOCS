package com.collaborative.editing.user.controller;

import com.collaborative.editing.common.dto.UserDTO;
import com.collaborative.editing.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Internal REST API for inter-service communication
 * These endpoints are meant to be called by other microservices, not external clients
 */
@RestController
@RequestMapping("/internal/users")
public class InternalUserController {
    
    @Autowired
    private UserService userService;

    /**
     * Internal endpoint to get user by ID (for service-to-service calls)
     * @param id User ID
     * @return UserDTO or error
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getUserByIdInternal(@PathVariable Long id) {
        try {
            UserDTO user = userService.getUserById(id);
            return ResponseEntity.ok(user);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }

    /**
     * Internal endpoint to verify user exists
     * @param id User ID
     * @return Boolean indicating if user exists
     */
    @GetMapping("/{id}/exists")
    public ResponseEntity<Map<String, Object>> userExists(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            UserDTO user = userService.getUserById(id);
            response.put("exists", true);
            // A user can exist but be inactive (soft-deleted). Expose that properly.
            response.put("active", user != null && Boolean.TRUE.equals(user.getActive()));
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            response.put("exists", false);
            response.put("active", false);
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Internal endpoint to get multiple users by IDs (batch operation)
     * @param ids Comma-separated user IDs
     * @return List of UserDTOs
     */
    @GetMapping("/batch")
    public ResponseEntity<List<UserDTO>> getUsersByIds(@RequestParam List<Long> ids) {
        List<UserDTO> users = userService.getUsersByIds(ids);
        return ResponseEntity.ok(users);
    }

    /**
     * Internal endpoint to check if user is admin
     * @param id User ID
     * @return Boolean indicating if user is admin
     */
    @GetMapping("/{id}/isAdmin")
    public ResponseEntity<Map<String, Object>> isAdmin(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            boolean isAdmin = userService.isAdmin(id);
            response.put("isAdmin", isAdmin);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("isAdmin", false);
            response.put("error", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Internal health check endpoint
     * @return Service status
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "UP");
        status.put("service", "user-management-service");
        return ResponseEntity.ok(status);
    }
}


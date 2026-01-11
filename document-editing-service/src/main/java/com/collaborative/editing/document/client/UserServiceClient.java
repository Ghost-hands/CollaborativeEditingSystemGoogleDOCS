package com.collaborative.editing.document.client;

import com.collaborative.editing.common.dto.UserDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * REST client for communicating with User Management Service
 */
@Component
public class UserServiceClient {
    
    private static final Logger logger = LoggerFactory.getLogger(UserServiceClient.class);
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Value("${services.user-management.url:http://localhost:8081}")
    private String userServiceUrl;

    /**
     * Get user by ID from User Management Service
     * @param userId User ID
     * @return UserDTO or null if not found
     */
    public UserDTO getUserById(Long userId) {
        logger.debug("Calling User Service to get user: ID={}", userId);
        try {
            String url = userServiceUrl + "/internal/users/" + userId;
            ResponseEntity<UserDTO> response = restTemplate.getForEntity(url, UserDTO.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                logger.debug("Successfully retrieved user: ID={}, username={}", 
                        userId, response.getBody().getUsername());
                return response.getBody();
            }
            logger.warn("User not found or invalid response: ID={}, status={}", userId, response.getStatusCode());
            return null;
        } catch (RestClientException e) {
            logger.error("Error calling User Service for user ID: {}", userId, e);
            return null;
        }
    }

    /**
     * Check if user exists and is active
     * @param userId User ID
     * @return true if user exists and is active, false otherwise
     */
    @SuppressWarnings("unchecked")
    public boolean userExists(Long userId) {
        try {
            String url = userServiceUrl + "/internal/users/" + userId + "/exists";
            ResponseEntity<Map<String, Object>> response = restTemplate.getForEntity(url, 
                    (Class<Map<String, Object>>) (Class<?>) Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                if (body != null) {
                    Boolean exists = (Boolean) body.get("exists");
                    Boolean active = (Boolean) body.get("active");
                    return exists != null && exists && active != null && active;
                }
            }
            return false;
        } catch (RestClientException e) {
            logger.error("Error checking user existence for user ID: {}", userId, e);
            return false;
        }
    }

    /**
     * Get multiple users by IDs (batch operation)
     * @param userIds List of user IDs
     * @return List of UserDTOs
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<UserDTO> getUsersByIds(List<Long> userIds) {
        try {
            String url = userServiceUrl + "/internal/users/batch";
            StringBuilder params = new StringBuilder();
            for (int i = 0; i < userIds.size(); i++) {
                if (i > 0) params.append(",");
                params.append(userIds.get(i));
            }
            url += "?ids=" + params.toString();
            
            ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                List body = response.getBody();
                if (body != null) {
                    // Convert List<Map> to List<UserDTO>
                    return body.stream()
                            .map(item -> {
                                Map<String, Object> map = (Map<String, Object>) item;
                                UserDTO dto = new UserDTO();
                                if (map.get("id") != null) dto.setId(Long.parseLong(map.get("id").toString()));
                                if (map.get("username") != null) dto.setUsername(map.get("username").toString());
                                if (map.get("email") != null) dto.setEmail(map.get("email").toString());
                                if (map.get("firstName") != null) dto.setFirstName(map.get("firstName").toString());
                                if (map.get("lastName") != null) dto.setLastName(map.get("lastName").toString());
                                if (map.get("profilePicture") != null) dto.setProfilePicture(map.get("profilePicture").toString());
                                return dto;
                            })
                            .toList();
                }
            }
            return List.of();
        } catch (RestClientException e) {
            logger.error("Error getting users by IDs: {}", userIds, e);
            return List.of();
        }
    }
    
    /**
     * Check if user is admin
     * @param userId User ID
     * @return true if user is admin, false otherwise
     */
    @SuppressWarnings("unchecked")
    public boolean isAdmin(Long userId) {
        try {
            String url = userServiceUrl + "/internal/users/" + userId + "/isAdmin";
            ResponseEntity<Map<String, Object>> response = restTemplate.getForEntity(url, 
                    (Class<Map<String, Object>>) (Class<?>) Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                if (body != null) {
                    Boolean isAdmin = (Boolean) body.get("isAdmin");
                    return isAdmin != null && isAdmin;
                }
            }
            return false;
        } catch (RestClientException e) {
            logger.error("Error checking admin status for user ID: {}", userId, e);
            return false;
        }
    }
}


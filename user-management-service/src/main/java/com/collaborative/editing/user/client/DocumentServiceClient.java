package com.collaborative.editing.user.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * REST client for communicating with Document Editing Service
 */
@Component
public class DocumentServiceClient {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentServiceClient.class);
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Value("${services.document-editing.url:http://localhost:8084}")
    private String documentServiceUrl;

    /**
     * Delete all documents owned by a user
     * @param ownerId User ID (owner of documents)
     * @return true if successful, false otherwise
     */
    @SuppressWarnings({"unchecked", "null"})
    public boolean deleteAllDocumentsByOwner(Long ownerId) {
        logger.debug("Calling Document Service to delete all documents for owner: ID={}", ownerId);
        try {
            String url = documentServiceUrl + "/internal/documents/owner/" + ownerId;
            ResponseEntity<Map<String, String>> response = restTemplate.exchange(
                    url, 
                    HttpMethod.DELETE,
                    null,
                    (Class<Map<String, String>>) (Class<?>) Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK) {
                logger.info("Successfully deleted all documents for owner: ID={}", ownerId);
                return true;
            }
            logger.warn("Failed to delete documents for owner: ID={}, status={}", ownerId, response.getStatusCode());
            return false;
        } catch (RestClientException e) {
            logger.error("Error calling Document Service to delete documents for owner ID: {}", ownerId, e);
            return false;
        }
    }
}


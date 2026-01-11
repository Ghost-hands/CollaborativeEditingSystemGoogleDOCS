package com.collaborative.editing.document.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * REST client for communicating with Version Control Service
 */
@Component
public class VersionServiceClient {
    
    private static final Logger logger = LoggerFactory.getLogger(VersionServiceClient.class);
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Value("${services.version-control.url:http://localhost:8083}")
    private String versionServiceUrl;

    /**
     * Create initial version 0 for a newly created document
     * @param documentId Document ID
     * @param content Document content
     * @param createdBy User ID who created the document
     * @return true if successful, false otherwise
     */
    public boolean createInitialVersion(Long documentId, String content, Long createdBy) {
        try {
            String url = versionServiceUrl + "/internal/versions/initial";
            Map<String, Object> request = new HashMap<>();
            request.put("documentId", documentId);
            request.put("content", content != null ? content : "");
            request.put("createdBy", createdBy);
            
            org.springframework.http.HttpEntity<Map<String, Object>> httpEntity = 
                    new org.springframework.http.HttpEntity<>(request);
            @SuppressWarnings({"null", "unchecked"})
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, 
                    org.springframework.http.HttpMethod.POST, 
                    httpEntity, 
                    (Class<Map<String, Object>>) (Class<?>) Map.class
            );
            
            return response.getStatusCode() == HttpStatus.CREATED || response.getStatusCode() == HttpStatus.OK;
        } catch (RestClientException e) {
            logger.error("Error creating initial version for document ID: {}", documentId, e);
            return false;
        }
    }

    /**
     * Delete all versions for a document
     * @param documentId Document ID
     * @return true if successful, false otherwise
     */
    public boolean deleteAllVersionsForDocument(Long documentId) {
        try {
            String url = versionServiceUrl + "/internal/versions/document/" + documentId;
            @SuppressWarnings({"null", "unchecked"})
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, 
                    org.springframework.http.HttpMethod.DELETE, 
                    null, 
                    (Class<Map<String, Object>>) (Class<?>) Map.class
            );
            
            return response.getStatusCode() == HttpStatus.OK;
        } catch (RestClientException e) {
            logger.error("Error deleting versions for document ID: {}", documentId, e);
            return false;
        }
    }
}


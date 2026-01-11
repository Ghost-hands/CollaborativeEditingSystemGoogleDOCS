package com.collaborative.editing.version.client;

import com.collaborative.editing.common.dto.ChangeTrackingDTO;
import com.collaborative.editing.common.dto.DocumentDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
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
     * Get document by ID from Document Editing Service
     * @param documentId Document ID
     * @return DocumentDTO or null if not found
     */
    public DocumentDTO getDocumentById(Long documentId) {
        try {
            String url = documentServiceUrl + "/internal/documents/" + documentId;
            ResponseEntity<DocumentDTO> response = restTemplate.getForEntity(url, DocumentDTO.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            }
            return null;
        } catch (RestClientException e) {
            logger.error("Error calling Document Service for document ID: {}", documentId, e);
            return null;
        }
    }

    /**
     * Check if document exists
     * @param documentId Document ID
     * @return true if document exists, false otherwise
     */
    @SuppressWarnings("unchecked")
    public boolean documentExists(Long documentId) {
        try {
            String url = documentServiceUrl + "/internal/documents/" + documentId + "/exists";
            ResponseEntity<Map<String, Boolean>> response = restTemplate.getForEntity(url, 
                    (Class<Map<String, Boolean>>) (Class<?>) Map.class);
            
            Map<String, Boolean> body = response.getBody();
            if (response.getStatusCode() == HttpStatus.OK && body != null) {
                Boolean exists = body.get("exists");
                return exists != null && exists;
            }
            return false;
        } catch (RestClientException e) {
            logger.error("Error checking document existence for document ID: {}", documentId, e);
            return false;
        }
    }

    /**
     * Update document content (used when reverting to a version)
     * @param documentId Document ID
     * @param content New document content
     * @return Updated DocumentDTO or null if error
     */
    public DocumentDTO updateDocumentContent(Long documentId, String content) {
        try {
            String url = documentServiceUrl + "/internal/documents/" + documentId + "/content";
            Map<String, String> request = new HashMap<>();
            request.put("content", content);
            
            org.springframework.http.HttpEntity<Map<String, String>> httpEntity = 
                    new org.springframework.http.HttpEntity<>(request);
            @SuppressWarnings("null")
            ResponseEntity<DocumentDTO> response = restTemplate.exchange(
                    url, 
                    org.springframework.http.HttpMethod.PUT, 
                    httpEntity, 
                    DocumentDTO.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            }
            return null;
        } catch (RestClientException e) {
            logger.error("Error updating document content for document ID: {}", documentId, e);
            return null;
        }
    }

    /**
     * Get unversioned changes for a document
     * @param documentId Document ID
     * @return List of unversioned changes or empty list on error
     */
    public List<ChangeTrackingDTO> getUnversionedChanges(Long documentId) {
        try {
            String url = documentServiceUrl + "/internal/documents/" + documentId + "/unversioned-changes";
            @SuppressWarnings("null")
            ResponseEntity<List<ChangeTrackingDTO>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<ChangeTrackingDTO>>() {}
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            }
            return List.of();
        } catch (RestClientException e) {
            logger.error("Error getting unversioned changes for document ID: {}", documentId, e);
            return List.of();
        }
    }

    /**
     * Link changes to a version
     * @param documentId Document ID
     * @param versionId Version ID
     * @return true if successful, false otherwise
     */
    public boolean linkChangesToVersion(Long documentId, Long versionId) {
        try {
            String url = documentServiceUrl + "/internal/documents/" + documentId + "/link-changes-to-version";
            Map<String, Long> request = new HashMap<>();
            request.put("versionId", versionId);
            
            org.springframework.http.HttpEntity<Map<String, Long>> httpEntity = 
                    new org.springframework.http.HttpEntity<>(request);
            @SuppressWarnings({"null", "unchecked"})
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, 
                    org.springframework.http.HttpMethod.POST, 
                    httpEntity, 
                    (Class<Map<String, Object>>) (Class<?>) Map.class
            );
            
            return response.getStatusCode() == HttpStatus.OK;
        } catch (RestClientException e) {
            logger.error("Error linking changes to version for document ID: {}, version ID: {}", 
                    documentId, versionId, e);
            return false;
        }
    }

    /**
     * Unlink changes from versions (for revert)
     * @param documentId Document ID
     * @param versionIds List of version IDs to unlink changes from
     * @return true if successful, false otherwise
     */
    public boolean unlinkChangesFromVersions(Long documentId, List<Long> versionIds) {
        try {
            String url = documentServiceUrl + "/internal/documents/" + documentId + "/unlink-changes-from-versions";
            Map<String, List<Long>> request = new HashMap<>();
            request.put("versionIds", versionIds);
            
            org.springframework.http.HttpEntity<Map<String, List<Long>>> httpEntity = 
                    new org.springframework.http.HttpEntity<>(request);
            @SuppressWarnings({"null", "unchecked"})
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, 
                    org.springframework.http.HttpMethod.POST, 
                    httpEntity, 
                    (Class<Map<String, Object>>) (Class<?>) Map.class
            );
            
            return response.getStatusCode() == HttpStatus.OK;
        } catch (RestClientException e) {
            logger.error("Error unlinking changes from versions for document ID: {}, version IDs: {}", 
                    documentId, versionIds, e);
            return false;
        }
    }

    /**
     * Get changes for a specific version
     * @param versionId Version ID
     * @return List of changes for the version or empty list on error
     */
    public List<ChangeTrackingDTO> getChangesByVersionId(Long versionId) {
        try {
            String url = documentServiceUrl + "/internal/documents/version/" + versionId + "/changes";
            @SuppressWarnings("null")
            ResponseEntity<List<ChangeTrackingDTO>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<ChangeTrackingDTO>>() {}
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            }
            return List.of();
        } catch (RestClientException e) {
            logger.error("Error getting changes for version ID: {}", versionId, e);
            return List.of();
        }
    }

    /**
     * Reset WebSocket state for a document (used after revert)
     * This ensures real-time editing works correctly after document content changes
     * @param documentId Document ID
     * @return true if successful, false otherwise
     */
    public boolean resetWebSocketState(Long documentId) {
        try {
            String url = documentServiceUrl + "/internal/documents/" + documentId + "/reset-websocket-state";
            @SuppressWarnings({"null", "unchecked"})
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    null,
                    (Class<Map<String, Object>>) (Class<?>) Map.class
            );
            
            return response.getStatusCode() == HttpStatus.OK;
        } catch (RestClientException e) {
            logger.error("Error resetting WebSocket state for document ID: {}", documentId, e);
            return false;
        }
    }
}


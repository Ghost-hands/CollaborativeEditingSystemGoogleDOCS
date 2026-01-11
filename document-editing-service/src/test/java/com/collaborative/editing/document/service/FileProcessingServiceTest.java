package com.collaborative.editing.document.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("File Processing Service Tests")
class FileProcessingServiceTest {

    @InjectMocks
    private FileProcessingService fileProcessingService;

    @Test
    @DisplayName("Extract content from text file")
    void testExtractFromTextFile() throws IOException {
        String content = "Hello World\nThis is a test file\nWith multiple lines";
        MultipartFile file = new MockMultipartFile(
            "file", 
            "test.txt", 
            "text/plain", 
            content.getBytes()
        );

        String result = fileProcessingService.extractContentFromFile(file, "txt");

        assertEquals(content, result);
    }

    @Test
    @DisplayName("Extract content from empty text file")
    void testExtractFromEmptyTextFile() throws IOException {
        String content = "";
        MultipartFile file = new MockMultipartFile(
            "file", 
            "empty.txt", 
            "text/plain", 
            content.getBytes()
        );

        String result = fileProcessingService.extractContentFromFile(file, "txt");

        assertEquals("", result);
    }

    @Test
    @DisplayName("Extract content from text file with UTF-8 characters")
    void testExtractFromTextFile_UTF8() throws IOException {
        String content = "Hello 世界\nTest with émojis 🎉";
        MultipartFile file = new MockMultipartFile(
            "file", 
            "utf8.txt", 
            "text/plain", 
            content.getBytes("UTF-8")
        );

        String result = fileProcessingService.extractContentFromFile(file, "txt");

        assertEquals(content, result);
    }

    @Test
    @DisplayName("Extract content from unsupported file type - should throw exception")
    void testExtractFromUnsupportedFileType() {
        MultipartFile file = new MockMultipartFile(
            "file", 
            "test.pdf", 
            "application/pdf", 
            "content".getBytes()
        );

        assertThrows(IllegalArgumentException.class, () -> {
            fileProcessingService.extractContentFromFile(file, "pdf");
        });
    }

    @Test
    @DisplayName("Create Word document from title and content")
    void testCreateWordDocument() throws IOException {
        String title = "Test Document";
        String content = "This is the content\nWith multiple lines\nOf text";

        byte[] result = fileProcessingService.createWordDocument(title, content);

        assertNotNull(result);
        assertTrue(result.length > 0);
    }

    @Test
    @DisplayName("Create Word document with empty content")
    void testCreateWordDocument_EmptyContent() throws IOException {
        String title = "Empty Document";
        String content = "";

        byte[] result = fileProcessingService.createWordDocument(title, content);

        assertNotNull(result);
        assertTrue(result.length > 0);
    }

    @Test
    @DisplayName("Create Word document with null content")
    void testCreateWordDocument_NullContent() throws IOException {
        String title = "Null Content Document";
        String content = null;

        byte[] result = fileProcessingService.createWordDocument(title, content);

        assertNotNull(result);
        assertTrue(result.length > 0);
    }

    @Test
    @DisplayName("Create Word document with long content")
    void testCreateWordDocument_LongContent() throws IOException {
        String title = "Long Document";
        StringBuilder contentBuilder = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            contentBuilder.append("Line ").append(i).append("\n");
        }
        String content = contentBuilder.toString();

        byte[] result = fileProcessingService.createWordDocument(title, content);

        assertNotNull(result);
        assertTrue(result.length > 0);
    }

    @Test
    @DisplayName("Create Word document with special characters")
    void testCreateWordDocument_SpecialCharacters() throws IOException {
        String title = "Special Characters Document";
        String content = "Test with special chars: @#$%^&*()\nAnd unicode: 世界 🎉";

        byte[] result = fileProcessingService.createWordDocument(title, content);

        assertNotNull(result);
        assertTrue(result.length > 0);
    }
}

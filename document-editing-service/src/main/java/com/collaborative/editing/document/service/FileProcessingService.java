package com.collaborative.editing.document.service;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class FileProcessingService {
    
    private static final Logger logger = LoggerFactory.getLogger(FileProcessingService.class);
    
    /**
     * Extract text content from uploaded file
     */
    public String extractContentFromFile(MultipartFile file, String fileExtension) throws IOException {
        if (fileExtension.equals("docx")) {
            return extractFromWordDocument(file);
        } else if (fileExtension.equals("txt")) {
            return extractFromTextFile(file);
        } else {
            throw new IllegalArgumentException("Unsupported file type: " + fileExtension);
        }
    }
    
    /**
     * Extract text from Word document (.docx)
     */
    private String extractFromWordDocument(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream();
             XWPFDocument document = new XWPFDocument(inputStream)) {
            
            StringBuilder content = new StringBuilder();
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            
            for (XWPFParagraph paragraph : paragraphs) {
                String text = paragraph.getText();
                if (text != null && !text.trim().isEmpty()) {
                    content.append(text).append("\n");
                }
            }
            
            // Remove trailing newline
            if (content.length() > 0 && content.charAt(content.length() - 1) == '\n') {
                content.setLength(content.length() - 1);
            }
            
            logger.debug("Extracted {} characters from Word document", content.length());
            return content.toString();
        }
    }
    
    /**
     * Extract text from plain text file (.txt)
     */
    private String extractFromTextFile(MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        String content = new String(bytes, StandardCharsets.UTF_8);
        logger.debug("Extracted {} characters from text file", content.length());
        return content;
    }
    
    /**
     * Create a Word document from title and content
     */
    public byte[] createWordDocument(String title, String content) throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            
            // Add title as a paragraph
            XWPFParagraph titleParagraph = document.createParagraph();
            titleParagraph.createRun().setText(title);
            titleParagraph.createRun().setBold(true);
            titleParagraph.createRun().setFontSize(16);
            
            // Add a blank line
            document.createParagraph();
            
            // Split content by newlines and add as paragraphs
            String[] lines = content.split("\n");
            for (String line : lines) {
                XWPFParagraph paragraph = document.createParagraph();
                paragraph.createRun().setText(line);
            }
            
            document.write(out);
            byte[] bytes = out.toByteArray();
            logger.debug("Created Word document with {} bytes", bytes.length);
            return bytes;
        }
    }
}


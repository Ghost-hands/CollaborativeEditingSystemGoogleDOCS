package com.collaborative.editing.version.service;

import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service to compute diffs between document versions (similar to GitHub)
 */
@Service
public class DiffService {
    
    /**
     * Represents a diff segment (addition, deletion, or unchanged)
     */
    public static class DiffSegment {
        public enum Type {
            ADDED,    // Green highlight
            REMOVED,  // Red highlight
            UNCHANGED // No highlight
        }
        
        private Type type;
        private String content;
        private int startLine;
        private int endLine;
        
        public DiffSegment(Type type, String content, int startLine, int endLine) {
            this.type = type;
            this.content = content;
            this.startLine = startLine;
            this.endLine = endLine;
        }
        
        public Type getType() { return type; }
        public String getContent() { return content; }
        public int getStartLine() { return startLine; }
        public int getEndLine() { return endLine; }
    }
    
    /**
     * Compute diff between two versions using line-by-line comparison
     * Similar to GitHub's diff view
     */
    public List<DiffSegment> computeDiff(String oldContent, String newContent) {
        if (oldContent == null) oldContent = "";
        if (newContent == null) newContent = "";
        
        String[] oldLines = oldContent.split("\n", -1); // -1 to preserve trailing empty lines
        String[] newLines = newContent.split("\n", -1);
        
        List<DiffSegment> segments = new ArrayList<>();
        
        // Use longest common subsequence (LCS) algorithm for better diff
        int[][] lcs = computeLCS(oldLines, newLines);
        List<int[]> matches = findMatches(oldLines, newLines, lcs);
        
        int oldIndex = 0;
        int newIndex = 0;
        int matchIndex = 0;
        
        while (oldIndex < oldLines.length || newIndex < newLines.length) {
            if (matchIndex < matches.size()) {
                int[] match = matches.get(matchIndex);
                int oldMatch = match[0];
                int newMatch = match[1];
                
                // Add removed lines (red)
                if (oldIndex < oldMatch) {
                    StringBuilder removed = new StringBuilder();
                    for (int i = oldIndex; i < oldMatch; i++) {
                        if (removed.length() > 0) removed.append("\n");
                        removed.append(oldLines[i]);
                    }
                    if (removed.length() > 0) {
                        segments.add(new DiffSegment(DiffSegment.Type.REMOVED, removed.toString(), oldIndex, oldMatch - 1));
                    }
                }
                
                // Add added lines (green)
                if (newIndex < newMatch) {
                    StringBuilder added = new StringBuilder();
                    for (int i = newIndex; i < newMatch; i++) {
                        if (added.length() > 0) added.append("\n");
                        added.append(newLines[i]);
                    }
                    if (added.length() > 0) {
                        segments.add(new DiffSegment(DiffSegment.Type.ADDED, added.toString(), newIndex, newMatch - 1));
                    }
                }
                
                // Add unchanged lines
                if (oldMatch < oldLines.length && newMatch < newLines.length) {
                    StringBuilder unchanged = new StringBuilder();
                    int unchangedStart = oldIndex;
                    int unchangedEnd = oldMatch;
                    for (int i = oldIndex; i <= oldMatch && i < oldLines.length; i++) {
                        if (unchanged.length() > 0) unchanged.append("\n");
                        unchanged.append(oldLines[i]);
                    }
                    if (unchanged.length() > 0 && oldMatch < oldLines.length) {
                        segments.add(new DiffSegment(DiffSegment.Type.UNCHANGED, unchanged.toString(), unchangedStart, unchangedEnd));
                    }
                }
                
                oldIndex = oldMatch + 1;
                newIndex = newMatch + 1;
                matchIndex++;
            } else {
                // No more matches - all remaining lines are changes
                if (oldIndex < oldLines.length) {
                    StringBuilder removed = new StringBuilder();
                    for (int i = oldIndex; i < oldLines.length; i++) {
                        if (removed.length() > 0) removed.append("\n");
                        removed.append(oldLines[i]);
                    }
                    if (removed.length() > 0) {
                        segments.add(new DiffSegment(DiffSegment.Type.REMOVED, removed.toString(), oldIndex, oldLines.length - 1));
                    }
                }
                if (newIndex < newLines.length) {
                    StringBuilder added = new StringBuilder();
                    for (int i = newIndex; i < newLines.length; i++) {
                        if (added.length() > 0) added.append("\n");
                        added.append(newLines[i]);
                    }
                    if (added.length() > 0) {
                        segments.add(new DiffSegment(DiffSegment.Type.ADDED, added.toString(), newIndex, newLines.length - 1));
                    }
                }
                break;
            }
        }
        
        return segments;
    }
    
    /**
     * Compute Longest Common Subsequence matrix
     */
    private int[][] computeLCS(String[] oldLines, String[] newLines) {
        int m = oldLines.length;
        int n = newLines.length;
        int[][] lcs = new int[m + 1][n + 1];
        
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (oldLines[i - 1].equals(newLines[j - 1])) {
                    lcs[i][j] = lcs[i - 1][j - 1] + 1;
                } else {
                    lcs[i][j] = Math.max(lcs[i - 1][j], lcs[i][j - 1]);
                }
            }
        }
        
        return lcs;
    }
    
    /**
     * Find matching lines using LCS
     */
    private List<int[]> findMatches(String[] oldLines, String[] newLines, int[][] lcs) {
        List<int[]> matches = new ArrayList<>();
        int i = oldLines.length;
        int j = newLines.length;
        
        while (i > 0 && j > 0) {
            if (oldLines[i - 1].equals(newLines[j - 1])) {
                matches.add(0, new int[]{i - 1, j - 1});
                i--;
                j--;
            } else if (lcs[i - 1][j] > lcs[i][j - 1]) {
                i--;
            } else {
                j--;
            }
        }
        
        return matches;
    }
    
    /**
     * Compute summary statistics for a diff
     */
    public Map<String, Object> computeDiffStats(String oldContent, String newContent) {
        List<DiffSegment> segments = computeDiff(oldContent, newContent);
        
        int addedLines = 0;
        int removedLines = 0;
        int addedChars = 0;
        int removedChars = 0;
        
        for (DiffSegment segment : segments) {
            if (segment.getType() == DiffSegment.Type.ADDED) {
                String[] lines = segment.getContent().split("\n", -1);
                addedLines += lines.length;
                addedChars += segment.getContent().length();
            } else if (segment.getType() == DiffSegment.Type.REMOVED) {
                String[] lines = segment.getContent().split("\n", -1);
                removedLines += lines.length;
                removedChars += segment.getContent().length();
            }
        }
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("addedLines", addedLines);
        stats.put("removedLines", removedLines);
        stats.put("addedChars", addedChars);
        stats.put("removedChars", removedChars);
        stats.put("netChange", addedChars - removedChars);
        
        return stats;
    }
}

package com.collaborative.editing.version.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Diff Service Tests")
class DiffServiceTest {

    @InjectMocks
    private DiffService diffService;

    @Test
    @DisplayName("Compute diff - identical content")
    void testComputeDiff_Identical() {
        String oldContent = "Hello World\nLine 2\nLine 3";
        String newContent = "Hello World\nLine 2\nLine 3";

        List<DiffService.DiffSegment> segments = diffService.computeDiff(oldContent, newContent);

        assertNotNull(segments);
        // Should have one unchanged segment
        assertTrue(segments.size() >= 1);
    }

    @Test
    @DisplayName("Compute diff - added lines")
    void testComputeDiff_AddedLines() {
        String oldContent = "Line 1\nLine 2";
        String newContent = "Line 1\nLine 2\nLine 3\nLine 4";

        List<DiffService.DiffSegment> segments = diffService.computeDiff(oldContent, newContent);

        assertNotNull(segments);
        assertTrue(segments.stream().anyMatch(s -> 
            s.getType() == DiffService.DiffSegment.Type.ADDED));
    }

    @Test
    @DisplayName("Compute diff - removed lines")
    void testComputeDiff_RemovedLines() {
        String oldContent = "Line 1\nLine 2\nLine 3";
        String newContent = "Line 1";

        List<DiffService.DiffSegment> segments = diffService.computeDiff(oldContent, newContent);

        assertNotNull(segments);
        assertTrue(segments.stream().anyMatch(s -> 
            s.getType() == DiffService.DiffSegment.Type.REMOVED));
    }

    @Test
    @DisplayName("Compute diff - modified lines")
    void testComputeDiff_ModifiedLines() {
        String oldContent = "Line 1\nLine 2\nLine 3";
        String newContent = "Line 1\nModified Line 2\nLine 3";

        List<DiffService.DiffSegment> segments = diffService.computeDiff(oldContent, newContent);

        assertNotNull(segments);
        assertTrue(segments.stream().anyMatch(s -> 
            s.getType() == DiffService.DiffSegment.Type.REMOVED));
        assertTrue(segments.stream().anyMatch(s -> 
            s.getType() == DiffService.DiffSegment.Type.ADDED));
    }

    @Test
    @DisplayName("Compute diff - empty to non-empty")
    void testComputeDiff_EmptyToNonEmpty() {
        String oldContent = "";
        String newContent = "Hello World\nLine 2";

        List<DiffService.DiffSegment> segments = diffService.computeDiff(oldContent, newContent);

        assertNotNull(segments);
        assertTrue(segments.stream().anyMatch(s -> 
            s.getType() == DiffService.DiffSegment.Type.ADDED));
    }

    @Test
    @DisplayName("Compute diff - non-empty to empty")
    void testComputeDiff_NonEmptyToEmpty() {
        String oldContent = "Hello World\nLine 2";
        String newContent = "";

        List<DiffService.DiffSegment> segments = diffService.computeDiff(oldContent, newContent);

        assertNotNull(segments);
        assertTrue(segments.stream().anyMatch(s -> 
            s.getType() == DiffService.DiffSegment.Type.REMOVED));
    }

    @Test
    @DisplayName("Compute diff - null old content")
    void testComputeDiff_NullOldContent() {
        String oldContent = null;
        String newContent = "Hello World";

        List<DiffService.DiffSegment> segments = diffService.computeDiff(oldContent, newContent);

        assertNotNull(segments);
        assertTrue(segments.stream().anyMatch(s -> 
            s.getType() == DiffService.DiffSegment.Type.ADDED));
    }

    @Test
    @DisplayName("Compute diff - null new content")
    void testComputeDiff_NullNewContent() {
        String oldContent = "Hello World";
        String newContent = null;

        List<DiffService.DiffSegment> segments = diffService.computeDiff(oldContent, newContent);

        assertNotNull(segments);
        assertTrue(segments.stream().anyMatch(s -> 
            s.getType() == DiffService.DiffSegment.Type.REMOVED));
    }

    @Test
    @DisplayName("Compute diff - both null")
    void testComputeDiff_BothNull() {
        String oldContent = null;
        String newContent = null;

        List<DiffService.DiffSegment> segments = diffService.computeDiff(oldContent, newContent);

        assertNotNull(segments);
    }

    @Test
    @DisplayName("Compute diff stats - added content")
    void testComputeDiffStats_AddedContent() {
        String oldContent = "Line 1";
        String newContent = "Line 1\nLine 2\nLine 3";

        Map<String, Object> stats = diffService.computeDiffStats(oldContent, newContent);

        assertNotNull(stats);
        assertTrue((Integer) stats.get("addedLines") > 0);
        assertTrue((Integer) stats.get("addedChars") > 0);
    }

    @Test
    @DisplayName("Compute diff stats - removed content")
    void testComputeDiffStats_RemovedContent() {
        String oldContent = "Line 1\nLine 2\nLine 3";
        String newContent = "Line 1";

        Map<String, Object> stats = diffService.computeDiffStats(oldContent, newContent);

        assertNotNull(stats);
        assertTrue((Integer) stats.get("removedLines") > 0);
        assertTrue((Integer) stats.get("removedChars") > 0);
    }

    @Test
    @DisplayName("Compute diff stats - modified content")
    void testComputeDiffStats_ModifiedContent() {
        String oldContent = "Old Line 1\nOld Line 2";
        String newContent = "New Line 1\nNew Line 2";

        Map<String, Object> stats = diffService.computeDiffStats(oldContent, newContent);

        assertNotNull(stats);
        assertTrue((Integer) stats.get("addedLines") > 0);
        assertTrue((Integer) stats.get("removedLines") > 0);
        assertNotNull(stats.get("netChange"));
    }

    @Test
    @DisplayName("Compute diff stats - identical content")
    void testComputeDiffStats_Identical() {
        String oldContent = "Line 1\nLine 2";
        String newContent = "Line 1\nLine 2";

        Map<String, Object> stats = diffService.computeDiffStats(oldContent, newContent);

        assertNotNull(stats);
        assertEquals(0, stats.get("addedLines"));
        assertEquals(0, stats.get("removedLines"));
        assertEquals(0, stats.get("netChange"));
    }

    @Test
    @DisplayName("Compute diff stats - empty content")
    void testComputeDiffStats_EmptyContent() {
        String oldContent = "";
        String newContent = "";

        Map<String, Object> stats = diffService.computeDiffStats(oldContent, newContent);

        assertNotNull(stats);
        assertEquals(0, stats.get("addedLines"));
        assertEquals(0, stats.get("removedLines"));
    }

    @Test
    @DisplayName("Compute diff - complex scenario with multiple changes")
    void testComputeDiff_ComplexScenario() {
        String oldContent = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5";
        String newContent = "Line 1\nModified Line 2\nLine 3\nNew Line 4.5\nLine 5\nLine 6";

        List<DiffService.DiffSegment> segments = diffService.computeDiff(oldContent, newContent);

        assertNotNull(segments);
        // Should have both additions and removals
        boolean hasAdded = segments.stream().anyMatch(s -> 
            s.getType() == DiffService.DiffSegment.Type.ADDED);
        boolean hasRemoved = segments.stream().anyMatch(s -> 
            s.getType() == DiffService.DiffSegment.Type.REMOVED);
        assertTrue(hasAdded || hasRemoved);
    }

    @Test
    @DisplayName("Compute diff - single line change")
    void testComputeDiff_SingleLineChange() {
        String oldContent = "Hello";
        String newContent = "Hello World";

        List<DiffService.DiffSegment> segments = diffService.computeDiff(oldContent, newContent);

        assertNotNull(segments);
        assertTrue(segments.stream().anyMatch(s -> 
            s.getType() == DiffService.DiffSegment.Type.ADDED));
    }

    @Test
    @DisplayName("Compute diff - preserve trailing empty lines")
    void testComputeDiff_TrailingEmptyLines() {
        String oldContent = "Line 1\nLine 2\n\n";
        String newContent = "Line 1\nLine 2\n\n\n";

        List<DiffService.DiffSegment> segments = diffService.computeDiff(oldContent, newContent);

        assertNotNull(segments);
    }
}

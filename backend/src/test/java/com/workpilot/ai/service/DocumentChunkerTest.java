package com.workpilot.ai.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentChunkerTest {

    private final DocumentChunker chunker = new DocumentChunker();

    @Test
    void keepsOrderOverlapPagesAndSections() {
        String text = "# Budget\n" + "a".repeat(750) + "\n\n" + "b".repeat(200);
        List<DocumentChunker.TextChunk> chunks = chunker.split(text);
        assertTrue(chunks.size() >= 2);
        assertEquals(0, chunks.get(0).index());
        assertEquals(1, chunks.get(1).index());
        assertEquals("Budget", chunks.get(0).sectionLabel());
        assertNull(chunks.get(0).pageNumber());
        String overlap = chunks.get(0).content().substring(chunks.get(0).content().length() - 40);
        assertTrue(chunks.get(1).content().contains(overlap) || chunks.get(0).content().endsWith("\n"));

        List<DocumentChunker.TextChunk> pages = chunker.split("alpha page\fbeta page");
        assertEquals(2, pages.size());
        assertEquals(1, pages.get(0).pageNumber());
        assertEquals(2, pages.get(1).pageNumber());
        assertTrue(pages.get(0).content().contains("alpha page"));
        assertTrue(pages.get(1).content().contains("beta page"));
    }
}

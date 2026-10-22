package com.workpilot.ai.service;

import java.util.ArrayList;
import java.util.List;

public class DocumentChunker {

    public static final int CHUNK_CHARACTERS = 800;
    public static final int OVERLAP_CHARACTERS = 100;

    public record TextChunk(int index, String content, Integer pageNumber, String sectionLabel) {
    }

    public List<TextChunk> split(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<TextChunk> chunks = new ArrayList<>();
        String[] pages = text.split("\f", -1);
        boolean paged = pages.length > 1 || text.indexOf('\f') >= 0;
        for (int pageIndex = 0; pageIndex < pages.length; pageIndex++) {
            Integer pageNumber = paged ? pageIndex + 1 : null;
            appendPage(chunks, pages[pageIndex], pageNumber);
        }
        return List.copyOf(chunks);
    }

    private static void appendPage(List<TextChunk> chunks, String page, Integer pageNumber) {
        String normalized = page.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (normalized.isEmpty()) {
            return;
        }
        String section = null;
        int cursor = 0;
        while (cursor < normalized.length()) {
            int end = Math.min(normalized.length(), cursor + CHUNK_CHARACTERS);
            if (end < normalized.length()) {
                int breakAt = normalized.lastIndexOf("\n\n", end);
                if (breakAt > cursor + CHUNK_CHARACTERS / 2) {
                    end = breakAt;
                }
            }
            String slice = normalized.substring(cursor, end).trim();
            if (!slice.isEmpty()) {
                section = latestHeading(slice, section);
                chunks.add(new TextChunk(chunks.size(), slice, pageNumber, headingLabel(section)));
            }
            if (end >= normalized.length()) {
                break;
            }
            int next = end - OVERLAP_CHARACTERS;
            cursor = Math.max(cursor + 1, next);
        }
    }

    private static String latestHeading(String slice, String current) {
        String found = current;
        for (String line : slice.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ") || trimmed.startsWith("## ") || trimmed.startsWith("### ")) {
                found = trimmed.replaceFirst("^#+\\s+", "");
            }
        }
        return found;
    }

    private static String headingLabel(String heading) {
        if (heading == null || heading.isBlank()) {
            return null;
        }
        String trimmed = heading.trim();
        return trimmed.length() <= 200 ? trimmed : trimmed.substring(0, 200);
    }
}

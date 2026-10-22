package com.workpilot.ai.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.Set;

@Component
public class DocumentTextExtractor {

    static final int MAX_EXTRACTED_CHARS = 20_000;
    private static final Set<String> TEXT_TYPES = Set.of("text/plain", "text/csv", "text/markdown");

    public String extract(String contentType, byte[] bytes) {
        if (TEXT_TYPES.contains(contentType)) {
            return limit(decodeUtf8(bytes));
        }
        if ("application/pdf".equals(contentType)) {
            return limit(readPdf(bytes));
        }
        return null;
    }

    private static String readPdf(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            StringBuilder text = new StringBuilder();
            int pages = document.getNumberOfPages();
            for (int page = 1; page <= pages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                if (page > 1) {
                    text.append('\f');
                }
                text.append(stripper.getText(document));
            }
            return text.toString();
        } catch (IOException exception) {
            throw new DocumentProcessingException("Text extraction failed");
        }
    }

    private static String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new DocumentProcessingException("Text extraction failed");
        }
    }

    private static String limit(String text) {
        if (text == null) {
            return null;
        }
        String cleaned = text.replace("\u0000", "").trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        if (cleaned.length() > MAX_EXTRACTED_CHARS) {
            return cleaned.substring(0, MAX_EXTRACTED_CHARS);
        }
        return cleaned;
    }
}

package org.example.service;

import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

@Service
public class TextExtractorService {
    private static final int MAX_EXTRACTED_TEXT_LENGTH = 60_000;

    private final Tika tika = new Tika();

    public String extractText(MultipartFile file) {
        try {
            return limitText(tika.parseToString(file.getInputStream()));
        } catch (Exception e) {
            throw new RuntimeException("Не удалось извлечь текст из файла: " + e.getMessage(), e);
        }
    }

    public String extractText(Path file) {
        if (file == null) {
            return "";
        }
        try {
            return limitText(tika.parseToString(file));
        } catch (Exception e) {
            throw new GenerationNonRetryableException("Не удалось извлечь текст из файла: " + file.getFileName(), e);
        }
    }

    private String limitText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.length() > MAX_EXTRACTED_TEXT_LENGTH) {
            return trimmed.substring(0, MAX_EXTRACTED_TEXT_LENGTH);
        }
        return trimmed;
    }
}

package org.example.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestionGenerationTextSanitizationTest {
    @Test
    void removesNullBytesBeforeGeneratedTextIsPersisted() {
        assertEquals("AB", QuestionGenerationService.normalizeDatabaseText("A\u0000B"));
        assertEquals("", QuestionGenerationService.normalizeDatabaseText(null));
    }
}

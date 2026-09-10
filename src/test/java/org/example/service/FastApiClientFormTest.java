package org.example.service;

import org.example.metrics.MetricsService;
import org.example.model.QuestionType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class FastApiClientFormTest {
    @Test
    void materialFlagIsSentSeparatelyFromPromptText() {
        FastApiClient client = new FastApiClient("http://localhost:8000", mock(MetricsService.class));

        String form = ReflectionTestUtils.invokeMethod(client, "buildGenerationFormBody",
                "Тема", 4, QuestionType.HUNDRED_TO_ONE, true);

        assertNotNull(form);
        assertTrue(form.contains("question_types=100k1"));
        assertTrue(form.contains("has_material=true"));
        assertFalse(form.contains("Учебный+материал+для+генерации"));
    }
}

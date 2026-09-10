package org.example.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AttemptServiceMultipleChoiceScoringTest {

    @Test
    void awardsOnePointOnlyForAnExactSelection() {
        Set<Long> correct = Set.of(1L, 2L);

        assertEquals(1, AttemptService.calculateMultipleChoiceScore(List.of(1L, 2L), correct));
        assertEquals(0, AttemptService.calculateMultipleChoiceScore(List.of(1L), correct));
        assertEquals(0, AttemptService.calculateMultipleChoiceScore(List.of(1L, 2L, 3L), correct));
    }
}

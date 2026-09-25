package org.example.dto.ml;

import java.math.BigDecimal;

public record MlQuestionOptionDTO(
        String id,
        String text,
        BigDecimal nominal
) {}

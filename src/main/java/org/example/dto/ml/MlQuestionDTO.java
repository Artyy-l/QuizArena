package org.example.dto.ml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MlQuestionDTO(
        String id,
        String type,
        String difficulty,
        String question,
        List<MlQuestionOptionDTO> options,
        List<String> correct_answers,
        String explanation,
        String source_reference,
        Map<String, Object> metadata
) {}

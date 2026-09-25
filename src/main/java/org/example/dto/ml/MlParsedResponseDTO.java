package org.example.dto.ml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MlParsedResponseDTO(
        String topic,
        String language,
        Integer question_count,
        List<MlQuestionDTO> questions
) {}

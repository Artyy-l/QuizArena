package org.example.dto.ml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MlJobResultDTO(
        String raw_response,
        @JsonProperty("parsed_response")
        MlParsedResponseDTO parsed_response
) {}

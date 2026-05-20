package org.example.dto.ml;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MlJobStateDTO(
        @JsonAlias("job_id")
        String id,
        String status,
        MlJobResultDTO result,
        List<String> errors
) {}

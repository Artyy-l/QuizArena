package org.example.dto.common;

import java.time.LocalDateTime;

public record GenerationMetadata(LocalDateTime generatedAt, String modelVersion,
                                 String promptHash) {}

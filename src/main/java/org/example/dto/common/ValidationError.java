package org.example.dto.common;

public record ValidationError(String questionText, String error, String field) {}

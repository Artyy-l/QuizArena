package org.example.service;

public class GenerationNonRetryableException extends RuntimeException {
    public GenerationNonRetryableException(String message) {
        super(message);
    }

    public GenerationNonRetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}

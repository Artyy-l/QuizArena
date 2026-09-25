package org.example.service;

public class GenerationRetryableException extends RuntimeException {
    public GenerationRetryableException(String message) {
        super(message);
    }

    public GenerationRetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}

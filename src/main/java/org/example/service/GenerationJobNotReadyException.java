package org.example.service;

public class GenerationJobNotReadyException extends GenerationRetryableException {
    public GenerationJobNotReadyException(String message) {
        super(message);
    }
}

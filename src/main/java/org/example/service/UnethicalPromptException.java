package org.example.service;

public class UnethicalPromptException extends RuntimeException {
    public UnethicalPromptException(String message) {
        super(message);
    }
    
    public UnethicalPromptException() {
        super("Данные квиза являются неэтичными");
    }
}

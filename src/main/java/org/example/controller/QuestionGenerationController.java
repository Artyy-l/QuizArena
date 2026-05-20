package org.example.controller;

import org.example.dto.request.generation.QuestionGenerationRequest;
import org.example.dto.response.generation.*;
import org.example.service.QuestionGenerationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/generation")
public class QuestionGenerationController {

    private final QuestionGenerationService questionGenerationService;

    @Autowired
    public QuestionGenerationController(QuestionGenerationService questionGenerationService) {
        this.questionGenerationService = questionGenerationService;
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generateQuestions(@RequestBody QuestionGenerationRequest request) {
        try {
            QuestionGenerationResponse response = questionGenerationService.generateQuizQuestionsKafka(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Ошибка при генерации вопросов: " + e.getMessage());
        }
    }

    @GetMapping("/{questionSetId}/validate")
    public ResponseEntity<ValidationResponse> validateQuestions(@PathVariable Long questionSetId) {
        try {
            ValidationResponse response = questionGenerationService.validateGeneratedQuestions(questionSetId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{questionSetId}/deduplicate")
    public ResponseEntity<DeduplicationResponse> deduplicateQuestions(@PathVariable Long questionSetId) {
        try {
            DeduplicationResponse response = questionGenerationService.removeDuplicateQuestions(questionSetId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{questionSetId}")
    public ResponseEntity<GeneratedQuestionsDTO> getGeneratedQuestions(@PathVariable Long questionSetId) {
        try {
            GeneratedQuestionsDTO questions = questionGenerationService.getGeneratedQuestions(questionSetId);
            return ResponseEntity.ok(questions);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}

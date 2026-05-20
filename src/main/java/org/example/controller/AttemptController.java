package org.example.controller;

import org.example.dto.request.attempt.StartAttemptRequest;
import org.example.dto.request.attempt.SubmitAnswerRequest;
import org.example.dto.request.attempt.SubmitStakeRequest;
import org.example.dto.response.attempt.AnswerResponse;
import org.example.dto.response.attempt.AttemptResponse;
import org.example.dto.response.attempt.QuizResultDTO;
import org.example.dto.response.quiz.QuestionDTO;
import org.example.service.AttemptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/attempts")
public class AttemptController {

    private final AttemptService attemptService;

    @Autowired
    public AttemptController(AttemptService attemptService) {
        this.attemptService = attemptService;
    }

    @PostMapping("/start")
    public ResponseEntity<AttemptResponse> startAttempt(@RequestBody StartAttemptRequest request) {
        try {
            AttemptResponse response = attemptService.startQuizAttempt(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{attemptId}/next")
    public ResponseEntity<QuestionDTO> getNextQuestion(@PathVariable Long attemptId) {
        try {
            QuestionDTO question = attemptService.getNextQuestion(attemptId);
            return question != null ? ResponseEntity.ok(question) : ResponseEntity.noContent().build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/answer")
    public ResponseEntity<AnswerResponse> submitAnswer(@RequestBody SubmitAnswerRequest request) {
        try {
            AnswerResponse response = attemptService.submitAnswer(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/stake")
    public ResponseEntity<?> submitStake(@RequestBody SubmitStakeRequest request) {
        try {
            QuestionDTO question = attemptService.submitStake(request);
            return ResponseEntity.ok(question);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message",
                    e.getMessage() != null ? e.getMessage() : "Неверные параметры ставки"));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message",
                    e.getMessage() != null ? e.getMessage() : "Невозможно сделать ставку"));
        }
    }

    @GetMapping("/{attemptId}/score")
    public ResponseEntity<?> getCurrentScore(@PathVariable Long attemptId) {
        try {
            double score = attemptService.getCurrentScore(attemptId);
            return ResponseEntity.ok(java.util.Map.of("score", (int) score));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{attemptId}/finish")
    public ResponseEntity<QuizResultDTO> finishAttempt(@PathVariable Long attemptId) {
        try {
            QuizResultDTO result = attemptService.finishQuizAttempt(attemptId);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}

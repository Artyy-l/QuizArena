package org.example.controller;

import org.example.dto.request.auth.UpdateProfileRequest;
import org.example.dto.response.auth.UserProfileDTO;
import org.example.dto.response.history.UserHistoryDTO;
import org.example.dto.response.quiz.QuizDTO;
import org.example.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{userId}/profile")
    public ResponseEntity<?> getUserProfile(@PathVariable Long userId) {
        try {
            if (userId == null || userId <= 0) {
                return ResponseEntity.badRequest().body("Некорректный ID пользователя");
            }
            
            UserProfileDTO profile = userService.getUserProfile(userId);
            return ResponseEntity.ok(profile);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Внутренняя ошибка сервера");
        }
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateUserProfile(@RequestBody UpdateProfileRequest request) {
        try {
            if (request.userId() == null) {
                return ResponseEntity.badRequest().body("ID пользователя обязателен");
            }
            
            UserProfileDTO profile = userService.updateUserProfile(request);
            return ResponseEntity.ok(profile);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Внутренняя ошибка сервера");
        }
    }

    @GetMapping("/{userId}/history")
    public ResponseEntity<?> getUserHistory(@PathVariable Long userId) {
        try {
            if (userId == null || userId <= 0) {
                return ResponseEntity.badRequest().body("Некорректный ID пользователя");
            }
            
            UserHistoryDTO history = userService.getUserHistory(userId);
            return ResponseEntity.ok(history);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Внутренняя ошибка сервера");
        }
    }

    @GetMapping("/{userId}/quizzes")
    public ResponseEntity<?> getCreatedQuizzes(@PathVariable Long userId) {
        try {
            if (userId == null || userId <= 0) {
                return ResponseEntity.badRequest().body("Некорректный ID пользователя");
            }
            
            List<QuizDTO> quizzes = userService.getCreatedQuizzes(userId);
            return ResponseEntity.ok(quizzes);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Внутренняя ошибка сервера");
        }
    }
}

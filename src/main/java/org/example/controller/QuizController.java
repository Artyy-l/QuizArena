package org.example.controller;

import org.example.dto.request.quiz.*;
import org.example.dto.response.quiz.*;
import org.example.security.RequestAuthorization;
import org.example.service.FileStorageService;
import org.example.service.GenerationNonRetryableException;
import org.example.service.GenerationRetryableException;
import org.example.service.QuestionGenerationService;
import org.example.service.QuizService;
import org.example.service.UnethicalPromptException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.FileSystemResource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Locale;

@RestController
@RequestMapping("/api/quizzes")
public class QuizController {

    private final QuizService quizService;
    private final FileStorageService fileStorageService;
    private final QuestionGenerationService questionGenerationService;
    private final RequestAuthorization authorization;

    @Autowired
    public QuizController(QuizService quizService, FileStorageService fileStorageService,
                          QuestionGenerationService questionGenerationService,
                          RequestAuthorization authorization) {
        this.quizService = quizService;
        this.fileStorageService = fileStorageService;
        this.questionGenerationService = questionGenerationService;
        this.authorization = authorization;
    }

    @PostMapping
    public ResponseEntity<?> createQuiz(@RequestBody CreateQuizRequest request,
                                        HttpServletRequest httpRequest) {
        authorization.requireSameUser(httpRequest, request.createdBy());
        try {
            if (request.name() == null || request.name().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Название квиза не может быть пустым");
            }
            if (request.createdBy() == null || request.createdBy() <= 0) {
                return ResponseEntity.badRequest().body("Некорректный ID создателя");
            }
            
            QuizResponseDTO response = quizService.createQuiz(request);
            return ResponseEntity.ok(response);
        } catch (UnethicalPromptException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Промпт не прошёл проверку безопасности. Измените формулировку");
        } catch (GenerationRetryableException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Проверка безопасности временно недоступна");
        } catch (GenerationNonRetryableException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            Throwable cause = e.getCause();
            while (cause != null) {
                if (cause instanceof UnethicalPromptException) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body("Данные квиза являются неэтичными");
                }
                cause = cause.getCause();
            }

            String errorMessage = e.getMessage();
            if (errorMessage != null && (errorMessage.contains("неэтичн") || errorMessage.contains("UNETHICAL_PROMPT") || errorMessage.contains("неэтичными"))) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Данные квиза являются неэтичными");
            }

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Внутренняя ошибка сервера: " + e.getMessage());
        }
    }

    /**
     * Создаёт квиз с материалом за один запрос. Прежний сценарий из двух запросов
     * мог оставить пустой квиз, если загрузка файла не выполнялась. Теперь квиз
     * не попадает под очистку, пока исходный файл не сохранён и не проверен.
     */
    @PostMapping(value = "/with-material", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createQuizWithMaterial(
            @RequestPart("quiz") CreateQuizRequest request,
            @RequestPart("material") MultipartFile material,
            HttpServletRequest httpRequest) {
        authorization.requireSameUser(httpRequest, request.createdBy());
        Long createdQuizId = null;
        try {
            if (request.name() == null || request.name().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Название квиза не может быть пустым");
            }
            if (request.createdBy() == null || request.createdBy() <= 0) {
                return ResponseEntity.badRequest().body("Некорректный ID создателя");
            }
            fileStorageService.validateUploadedMaterial(material);

            CreateQuizRequest materialRequest = new CreateQuizRequest(
                    request.name(), request.prompt(), request.createdBy(), true,
                    request.materials(), request.questionNumber(), request.timeLimit(),
                    request.isPrivate(), request.isStatic(), request.defaultQuestionType()
            );
            QuizResponseDTO created = quizService.createQuiz(materialRequest);
            createdQuizId = created.quizId();

            List<String> fileUrls = fileStorageService.saveQuizMaterials(
                    new MultipartFile[]{material}, createdQuizId);
            if (fileUrls.size() != 1) {
                throw new IllegalArgumentException("Файл пустой");
            }
            String fileUrl = fileUrls.get(0);
            Path storedFile = fileStorageService.resolveQuizMaterialUrl(createdQuizId, fileUrl);
            questionGenerationService.checkUploadedMaterial(
                    quizService.getQuizPrompt(createdQuizId), storedFile);
            quizService.updateQuizMaterialUrl(
                    createdQuizId, fileUrl, material.getOriginalFilename());
            quizService.regenerateWithMaterial(createdQuizId, null);

            return ResponseEntity.ok(created);
        } catch (UnethicalPromptException e) {
            cleanupCreatedQuiz(createdQuizId, request.createdBy());
            return ResponseEntity.badRequest().body("Материал или промпт не прошли проверку безопасности");
        } catch (GenerationNonRetryableException e) {
            cleanupCreatedQuiz(createdQuizId, request.createdBy());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (GenerationRetryableException e) {
            cleanupCreatedQuiz(createdQuizId, request.createdBy());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Проверка безопасности временно недоступна");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cleanupCreatedQuiz(createdQuizId, request.createdBy());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Проверка безопасности была прервана");
        } catch (IllegalArgumentException e) {
            cleanupCreatedQuiz(createdQuizId, request.createdBy());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IOException e) {
            cleanupCreatedQuiz(createdQuizId, request.createdBy());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Ошибка сохранения или проверки материала: " + e.getMessage());
        } catch (Exception e) {
            cleanupCreatedQuiz(createdQuizId, request.createdBy());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Внутренняя ошибка сервера");
        }
    }

    private void cleanupCreatedQuiz(Long quizId, Long userId) {
        if (quizId == null || userId == null) {
            return;
        }
        try {
            quizService.deleteQuiz(new DeleteQuizRequest(quizId, userId));
        } catch (Exception ignored) {
            // Ошибка очистки не должна подменить исходную ошибку проверки или генерации.
        }
    }

    @PostMapping("/safety-check/prompt")
    public ResponseEntity<?> checkPromptSafety(
            @RequestParam(required = false, defaultValue = "") String prompt,
            @RequestParam(required = false, defaultValue = "false") boolean hasMaterial,
            HttpServletRequest request) {
        authorization.requireUserId(request);
        try {
            questionGenerationService.checkPromptSafety(prompt, hasMaterial);
            return ResponseEntity.ok(Map.of(
                    "safe", true,
                    "message", "Промпт прошёл проверку безопасности"
            ));
        } catch (UnethicalPromptException e) {
            return ResponseEntity.ok(Map.of(
                    "safe", false,
                    "message", "Промпт не прошёл проверку безопасности. Измените формулировку"
            ));
        } catch (GenerationRetryableException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "safe", false,
                    "message", "Проверка промпта временно недоступна. Попробуйте ещё раз"
            ));
        } catch (GenerationNonRetryableException | IOException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "safe", false,
                    "message", "Не удалось проверить промпт"
            ));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "safe", false,
                    "message", "Проверка промпта была прервана"
            ));
        }
    }

    @PostMapping(value = "/safety-check/material", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> checkMaterialSafety(
            @RequestPart("material") MultipartFile material,
            HttpServletRequest request) {
        authorization.requireUserId(request);
        Path temporaryFile = null;
        try {
            fileStorageService.validateUploadedMaterial(material);
            String originalName = material.getOriginalFilename() != null
                    ? material.getOriginalFilename() : "material.bin";
            String extension = originalName.contains(".")
                    ? originalName.substring(originalName.lastIndexOf('.')).toLowerCase(Locale.ROOT)
                    : ".bin";
            temporaryFile = Files.createTempFile("quiz-safety-", extension);
            try (var input = material.getInputStream()) {
                Files.copy(input, temporaryFile, StandardCopyOption.REPLACE_EXISTING);
            }
            questionGenerationService.checkUploadedMaterial("", temporaryFile);
            return ResponseEntity.ok(Map.of(
                    "safe", true,
                    "message", "Материал прошёл проверку безопасности"
            ));
        } catch (UnethicalPromptException e) {
            return ResponseEntity.ok(Map.of(
                    "safe", false,
                    "message", "Материал не прошёл проверку безопасности"
            ));
        } catch (GenerationRetryableException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "safe", false,
                    "message", "Проверка материала временно недоступна. Попробуйте ещё раз"
            ));
        } catch (GenerationNonRetryableException | IllegalArgumentException | IOException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "safe", false,
                    "message", e.getMessage() != null ? e.getMessage() : "Не удалось проверить материал"
            ));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "safe", false,
                    "message", "Проверка материала была прервана"
            ));
        } finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    @PostMapping("/{quizId}/materials")
    public ResponseEntity<?> uploadQuizMaterials(
            @PathVariable Long quizId,
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "initial", defaultValue = "false") boolean initial,
            HttpServletRequest request) {
        authorization.requireQuizOwner(request, quizId);
        try {
            if (quizId == null || quizId <= 0) {
                return ResponseEntity.badRequest().body("Некорректный ID квиза");
            }

            if (files == null || files.length != 1) {
                throw new IllegalArgumentException("Загрузите один файл с материалом");
            }

            List<String> fileUrls = fileStorageService.saveQuizMaterials(files, quizId);
            if (fileUrls.isEmpty()) {
                throw new IllegalArgumentException("Файл пустой");
            }

            String newFileUrl = fileUrls.get(0);
            Path newFile = fileStorageService.resolveQuizMaterialUrl(quizId, newFileUrl);
            try {
                questionGenerationService.checkUploadedMaterial(quizService.getQuizPrompt(quizId), newFile);
            } catch (IOException | InterruptedException | RuntimeException e) {
                try {
                    Files.deleteIfExists(newFile);
                } catch (IOException cleanupFailure) {
                    e.addSuppressed(cleanupFailure);
                }
                throw e;
            }

            quizService.updateQuizMaterialUrl(quizId, newFileUrl, files[0].getOriginalFilename());
            quizService.regenerateWithMaterial(quizId, null);

            return ResponseEntity.ok(fileUrls);
        } catch (UnethicalPromptException e) {
            cleanupInitialQuiz(quizId, initial, request);
            return ResponseEntity.badRequest().body("Материал не прошёл проверку безопасности");
        } catch (GenerationNonRetryableException e) {
            cleanupInitialQuiz(quizId, initial, request);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (GenerationRetryableException e) {
            cleanupInitialQuiz(quizId, initial, request);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Проверка безопасности временно недоступна");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cleanupInitialQuiz(quizId, initial, request);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Проверка безопасности была прервана");
        } catch (IllegalArgumentException e) {
            cleanupInitialQuiz(quizId, initial, request);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IOException e) {
            cleanupInitialQuiz(quizId, initial, request);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Ошибка сохранения или проверки материала: " + e.getMessage());
        } catch (Exception e) {
            cleanupInitialQuiz(quizId, initial, request);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Внутренняя ошибка сервера");
        }
    }

    private void cleanupInitialQuiz(Long quizId, boolean initial, HttpServletRequest request) {
        if (!initial) {
            return;
        }
        try {
            quizService.deleteQuiz(new DeleteQuizRequest(quizId, authorization.requireUserId(request)));
        } catch (Exception cleanupFailure) {
            // Ошибка очистки не должна подменить исходную ошибку загрузки или проверки.
        }
    }

    @PostMapping("/search")
    public ResponseEntity<QuizSearchResponse> searchPublicQuizzes(@RequestBody QuizSearchRequest request) {
        QuizSearchResponse response = quizService.searchPublicQuizzes(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{quizId}")
    public ResponseEntity<?> getQuiz(@PathVariable Long quizId, HttpServletRequest request) {
        try {
            if (quizId == null || quizId <= 0) {
                return ResponseEntity.badRequest().body("Некорректный ID квиза");
            }
            
            Long userId = authorization.requireUserId(request);
            QuizDetailsDTO quiz = quizService.getQuiz(quizId, userId);
            return ResponseEntity.ok(quiz);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Доступ запрещен");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Внутренняя ошибка сервера");
        }
    }

    @GetMapping("/{quizId}/materials/download")
    public ResponseEntity<?> downloadQuizMaterial(@PathVariable Long quizId, HttpServletRequest request) {
        Long userId = authorization.requireUserId(request);
        try {
            QuizService.QuizMaterialDownload download = quizService.getQuizMaterialDownload(quizId, userId);
            Path path = download.path();
            String storedName = path.getFileName().toString();
            return ResponseEntity.ok()
                    .contentType(MediaTypeFactory.getMediaType(storedName)
                            .orElse(MediaType.APPLICATION_OCTET_STREAM))
                    .contentLength(Files.size(path))
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(download.originalFilename(), StandardCharsets.UTF_8).build().toString())
                    .body(new FileSystemResource(path));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{quizId}/generation-status")
    public ResponseEntity<?> getGenerationStatus(@PathVariable Long quizId, HttpServletRequest request) {
        try {
            if (quizId == null || quizId <= 0) {
                return ResponseEntity.badRequest().body("Некорректный ID квиза");
            }
            Long userId = authorization.requireUserId(request);
            quizService.getQuiz(quizId, userId);
            return ResponseEntity.ok(quizService.getGenerationStatus(quizId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Доступ запрещен");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Внутренняя ошибка сервера");
        }
    }

    @DeleteMapping("/{quizId}")
    public ResponseEntity<?> deleteQuiz(
            @PathVariable Long quizId,
            @RequestParam(required = false) Long userId,
            HttpServletRequest httpRequest) {
        Long currentUserId = authorization.requireUserId(httpRequest);
        if (userId != null) {
            authorization.requireSameUser(httpRequest, userId);
        }
        try {
            if (quizId == null || quizId <= 0) {
                return ResponseEntity.badRequest().body("Некорректный ID квиза");
            }
            DeleteQuizRequest request = new DeleteQuizRequest(quizId, currentUserId);
            boolean deleted = quizService.deleteQuiz(request);
            return ResponseEntity.ok(deleted);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Только создатель может удалить квиз");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Внутренняя ошибка сервера");
        }
    }

    @PutMapping("/{quizId}")
    public ResponseEntity<?> updateQuiz(
            @PathVariable Long quizId,
            @RequestBody UpdateQuizRequest request,
            HttpServletRequest httpRequest) {
        Long currentUserId = authorization.requireSameUser(httpRequest, request.userId());
        try {
            UpdateQuizRequest updatedRequest = new UpdateQuizRequest(
                    quizId,
                    currentUserId,
                    request.name(),
                    request.prompt(),
                    request.questionNumber(),
                    request.timeLimit(),
                    request.isPrivate(),
                    request.isStatic(),
                    request.defaultQuestionType()
            );
            QuizResponseDTO response = quizService.updateQuizMetadata(updatedRequest);
            return ResponseEntity.ok(response);
        } catch (UnethicalPromptException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Данные квиза являются неэтичными");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Ошибка при обновлении квиза: " + e.getMessage());
        }
    }

    @DeleteMapping("/{quizId}/questions/{questionId}")
    public ResponseEntity<Boolean> removeQuestion(
            @PathVariable Long quizId,
            @PathVariable Long questionId,
            @RequestParam(required = false) Long userId,
            HttpServletRequest httpRequest) {
        Long currentUserId = authorization.requireUserId(httpRequest);
        if (userId != null) {
            authorization.requireSameUser(httpRequest, userId);
        }
        try {
            RemoveQuestionRequest request = new RemoveQuestionRequest(quizId, questionId, currentUserId);
            boolean removed = quizService.removeQuestionFromQuiz(request);
            return ResponseEntity.ok(removed);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @GetMapping("/{quizId}/leaderboard")
    public ResponseEntity<LeaderboardDTO> getQuizLeaderboard(
            @PathVariable Long quizId,
            HttpServletRequest request) {
        try {
            LeaderboardDTO leaderboard = quizService.getQuizLeaderboard(
                    quizId, authorization.requireUserId(request));
            return ResponseEntity.ok(leaderboard);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @PostMapping("/{quizId}/copy")
    public ResponseEntity<?> copyQuiz(
            @PathVariable Long quizId,
            @RequestParam(required = false) Long userId,
            HttpServletRequest httpRequest) {
        Long currentUserId = authorization.requireUserId(httpRequest);
        if (userId != null) {
            authorization.requireSameUser(httpRequest, userId);
        }
        try {
            if (quizId == null || quizId <= 0) {
                return ResponseEntity.badRequest().body("Некорректный ID квиза");
            }
            QuizResponseDTO response = quizService.copyQuiz(quizId, currentUserId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Внутренняя ошибка сервера");
        }
    }

    @GetMapping("/{quizId}/by-id")
    public ResponseEntity<?> getQuizById(
            @PathVariable Long quizId,
            HttpServletRequest request) {
        try {
            if (quizId == null || quizId <= 0) {
                return ResponseEntity.badRequest().body("Некорректный ID квиза");
            }
            
            QuizDetailsDTO quiz = quizService.getQuizById(quizId, authorization.requireUserId(request));
            return ResponseEntity.ok(quiz);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Внутренняя ошибка сервера");
        }
    }
}

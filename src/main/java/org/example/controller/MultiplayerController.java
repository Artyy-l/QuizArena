package org.example.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.dto.request.multiplayer.*;
import org.example.dto.response.multiplayer.MultiplayerResultsDTO;
import org.example.dto.response.multiplayer.MultiplayerSessionDTO;
import org.example.dto.response.multiplayer.ParticipantsDTO;
import org.example.security.RequestAuthorization;
import org.example.service.MultiplayerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/multiplayer")
public class MultiplayerController {

    private final MultiplayerService multiplayerService;
    private final RequestAuthorization authorization;

    @Autowired
    public MultiplayerController(MultiplayerService multiplayerService, RequestAuthorization authorization) {
        this.multiplayerService = multiplayerService;
        this.authorization = authorization;
    }

    @PostMapping("/sessions")
    public ResponseEntity<MultiplayerSessionDTO> createSession(@RequestBody CreateMultiplayerRequest request,
                                                               HttpServletRequest httpRequest) {
        authorization.requireSameUser(httpRequest, request.userId());
        try {
            MultiplayerSessionDTO session = multiplayerService.createMultiplayerSession(request);
            return ResponseEntity.ok(session);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<MultiplayerSessionDTO> getSession(@PathVariable String sessionId) {
        try {
            MultiplayerSessionDTO session = multiplayerService.getMultiplayerSession(sessionId);
            return ResponseEntity.ok(session);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/sessions/join")
    public ResponseEntity<?> joinSession(@RequestBody JoinMultiplayerRequest request,
                                         HttpServletRequest httpRequest) {
        authorization.requireSameUser(httpRequest, request.userId());
        try {
            boolean joined = multiplayerService.joinMultiplayerSession(request);
            return ResponseEntity.ok(joined);
        } catch (IllegalArgumentException e) {
            return badRequest(e, "Неверные параметры запроса");
        } catch (IllegalStateException e) {
            return badRequest(e, "Не удалось присоединиться к сессии");
        }
    }

    @GetMapping("/sessions/{sessionId}/participants")
    public ResponseEntity<ParticipantsDTO> getParticipants(@PathVariable String sessionId) {
        try {
            ParticipantsDTO participants = multiplayerService.getSessionParticipants(sessionId);
            return ResponseEntity.ok(participants);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/sessions/start")
    public ResponseEntity<?> startSession(@RequestBody StartMultiplayerRequest request,
                                          HttpServletRequest httpRequest) {
        authorization.requireSameUser(httpRequest, request.hostUserId());
        try {
            boolean started = multiplayerService.startMultiplayerSession(request);
            return ResponseEntity.ok(started);
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().contains("Недостаточно участников")) {
                return ResponseEntity.badRequest().body(Map.of("message", "Нельзя начать мультиплеер одному. Нужно минимум 2 участника."));
            }
            return badRequest(e, "Не удалось запустить сессию");
        } catch (IllegalArgumentException e) {
            return badRequest(e, "Неверные параметры запроса");
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Только хост может запустить сессию"));
        }
    }

    @GetMapping("/sessions/{sessionId}/results")
    public ResponseEntity<MultiplayerResultsDTO> getResults(@PathVariable String sessionId) {
        try {
            MultiplayerResultsDTO results = multiplayerService.getMultiplayerResults(sessionId);
            return ResponseEntity.ok(results);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/sessions/cancel")
    public ResponseEntity<Boolean> cancelSession(@RequestBody CancelMultiplayerRequest request,
                                                 HttpServletRequest httpRequest) {
        authorization.requireSameUser(httpRequest, request.hostUserId());
        try {
            boolean cancelled = multiplayerService.cancelMultiplayerSession(request);
            return ResponseEntity.ok(cancelled);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @GetMapping("/sessions/{sessionId}/progress")
    public ResponseEntity<Map<String, Object>> getProgress(@PathVariable String sessionId,
                                                           @RequestParam(required = false) Long questionId,
                                                           HttpServletRequest request) {
        try {
            Map<String, Object> progress = multiplayerService.getSessionProgress(
                    sessionId, questionId, authorization.requireUserId(request));
            return ResponseEntity.ok(progress);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/sessions/{sessionId}/live-leaderboard")
    public ResponseEntity<?> getLiveLeaderboard(@PathVariable String sessionId) {
        try {
            var leaderboard = multiplayerService.getSessionLiveLeaderboard(sessionId);
            return ResponseEntity.ok(leaderboard);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/sessions/{sessionId}/leave")
    public ResponseEntity<?> leaveSession(@PathVariable String sessionId,
                                          @RequestParam(required = false) Long userId,
                                          HttpServletRequest request) {
        Long currentUserId = authorization.requireUserId(request);
        if (userId != null) {
            authorization.requireSameUser(request, userId);
        }
        try {
            boolean left = multiplayerService.leaveMultiplayerSession(sessionId, currentUserId);
            return ResponseEntity.ok(left);
        } catch (IllegalArgumentException e) {
            return badRequest(e, "Неверные параметры");
        } catch (IllegalStateException e) {
            return badRequest(e, "Нельзя покинуть сессию");
        }
    }

    private ResponseEntity<Map<String, String>> badRequest(RuntimeException exception, String defaultMessage) {
        String message = exception.getMessage() != null ? exception.getMessage() : defaultMessage;
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }
}

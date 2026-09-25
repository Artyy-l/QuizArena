package org.example.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.dto.request.auth.LoginRequest;
import org.example.dto.request.auth.RegisterRequest;
import org.example.dto.response.auth.AuthResponse;
import org.example.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final int authCookieMaxAgeSeconds;

    @Autowired
    public AuthController(AuthService authService,
                          @Value("${quizarena.jwt.expiration-ms}") long jwtExpirationMs) {
        this.authService = authService;
        long seconds = jwtExpirationMs / 1000L;
        this.authCookieMaxAgeSeconds = (int) Math.min(seconds, Integer.MAX_VALUE);
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request, HttpServletRequest httpRequest,
                                      HttpServletResponse httpResponse) {
        try {
            if (request.username() == null || request.username().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(new ErrorResponse("Логин не может быть пустым"));
            }
            if (request.password() == null || request.password().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(new ErrorResponse("Пароль не может быть пустым"));
            }
            if (request.password().length() < 3) {
                return ResponseEntity.badRequest().body(new ErrorResponse("Пароль должен содержать минимум 3 символа"));
            }
            
            AuthResponse response = authService.register(request);
            
            addAuthCookie(httpRequest, httpResponse, response.token());
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            String message = e.getMessage();
            if (message.contains("уже существует")) {
                message = "Пользователь с таким логином уже существует. Пожалуйста, выберите другой логин.";
            }
            return ResponseEntity.badRequest().body(new ErrorResponse(message));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Внутренняя ошибка сервера"));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest,
                                   HttpServletResponse httpResponse) {
        try {
            if (request.username() == null || request.username().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(new ErrorResponse("Логин не может быть пустым"));
            }
            if (request.password() == null || request.password().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(new ErrorResponse("Пароль не может быть пустым"));
            }
            
            AuthResponse response = authService.login(request);
            
            addAuthCookie(httpRequest, httpResponse, response.token());
            
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Неверный логин или пароль"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Внутренняя ошибка сервера"));
        }
    }

    private void addAuthCookie(HttpServletRequest request, HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from("authToken", token)
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Strict")
                .path("/")
                .maxAge(authCookieMaxAgeSeconds)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private record ErrorResponse(String message) {}
}

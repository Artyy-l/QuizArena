package org.example.service;

import lombok.extern.slf4j.Slf4j;
import org.example.dto.request.auth.LoginRequest;
import org.example.dto.request.auth.RegisterRequest;
import org.example.dto.response.auth.AuthResponse;
import org.example.model.User;
import org.example.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Slf4j
@Service
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Autowired
    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse register(RegisterRequest request) {
        log.info("register() called, username={}", request.username());

        String login = request.username().trim();
        boolean exists = userRepository.existsByLogin(login);

        if (exists) {
            log.warn("Попытка регистрации с занятым логином: '{}'", login);
            throw new IllegalArgumentException("Пользователь с таким логином уже существует");
        }

        User user = new User();
        user.setLogin(login);
        user.setPasswordHash(passwordEncoder.encode(request.password()));

        user = userRepository.save(user);

        String token = jwtService.generateToken(user);

        log.info("Регистрация успешна, userId={}, username='{}'", user.getId(), user.getLogin());
        return new AuthResponse(user.getId(), user.getLogin(), token);
    }

    public AuthResponse login(LoginRequest request) {
        log.info("login() called, username={}", request.username());

        String login = request.username().trim();
        log.debug("Поиск пользователя с логином: '{}'", login);

        User user = userRepository.findByLogin(login)
                .orElseThrow(() -> {
                    log.warn("Пользователь с логином '{}' не найден", login);
                    return new SecurityException("Неверный логин или пароль");
                });

        log.debug("Пользователь найден в БД, id={}, login='{}'", user.getId(), user.getLogin());

        boolean passwordMatches = passwordEncoder.matches(request.password(), user.getPasswordHash());

        if (!passwordMatches) {
            log.warn("Неверный пароль для пользователя id={}, login='{}'", user.getId(), user.getLogin());
            throw new SecurityException("Неверный логин или пароль");
        }

        String token = jwtService.generateToken(user);

        log.info("Логин успешен, userId={}, username='{}'", user.getId(), user.getLogin());
        return new AuthResponse(user.getId(), user.getLogin(), token);
    }
}

package org.example.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class QuizCacheEvictService {
    private static final String QUIZ_CACHE_KEY = "quiz:%d";

    private final RedisTemplate<String, String> redisTemplate;

    public QuizCacheEvictService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void evictQuizCache(Long quizId) {
        if (quizId == null) return;
        try {
            redisTemplate.delete(String.format(QUIZ_CACHE_KEY, quizId));
        } catch (Exception e) {
            log.debug("Не удалось инвалидировать кэш квиза {}", quizId, e);
        }
    }
}

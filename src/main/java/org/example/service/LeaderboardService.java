package org.example.service;

import org.example.dto.common.LeaderboardEntry;
import org.example.dto.response.quiz.LeaderboardDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
public class LeaderboardService {

    private static final String ZSET_KEY = "leaderboard:%d:%s";
    private static final String USERS_KEY = "leaderboard:users:%d:%s";

    private final RedisTemplate<String, String> redisTemplate;

    @Autowired
    public LeaderboardService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void updateLeaderboard(Long quizId, Long userId, String login, long score, long timeSpent) {
        updateLeaderboard(quizId, userId, login, score, timeSpent, null, null, "solo");
    }

    public void updateLeaderboard(Long quizId, Long userId, String login, long score, long timeSpent, String mode) {
        updateLeaderboard(quizId, userId, login, score, timeSpent, null, null, mode);
    }

    public void updateLeaderboard(Long quizId, Long userId, String login, long score, long timeSpent,
                                  Integer accuracyPercent, String mode) {
        updateLeaderboard(quizId, userId, login, score, timeSpent, accuracyPercent, null, mode);
    }

    public void updateLeaderboard(Long quizId, Long userId, String login, long score, long timeSpent,
                                  Integer accuracyPercent, Integer attemptNumber, String mode) {
        try {
            String normalizedMode = normalizeMode(mode);
            String zsetKey = String.format(ZSET_KEY, quizId, normalizedMode);
            String usersKey = String.format(USERS_KEY, quizId, normalizedMode);
            String userIdStr = String.valueOf(userId);

            int normalizedAccuracy = accuracyPercent != null ? accuracyPercent : 0;
            int normalizedAttemptNumber = attemptNumber != null ? attemptNumber : Integer.MAX_VALUE;
            double compositeScore = score * 1_000_000_000_000.0
                    + normalizedAccuracy * 1_000_000_000.0
                    - normalizedAttemptNumber * 1_000_000.0
                    - timeSpent;

            if (shouldUpdateLeaderboardEntry(usersKey, userIdStr, score, normalizedAccuracy, normalizedAttemptNumber, timeSpent)) {
                redisTemplate.opsForZSet().add(zsetKey, userIdStr, compositeScore);
                String accuracyPart = accuracyPercent != null ? String.valueOf(accuracyPercent) : "";
                String attemptPart = attemptNumber != null ? String.valueOf(attemptNumber) : "";
                redisTemplate.opsForHash().put(usersKey, userIdStr, login + ":" + score + ":" + timeSpent + ":" + accuracyPart + ":" + attemptPart);
            }
        } catch (Exception e) {
            log.debug("Не удалось обновить лидерборд квиза {}", quizId, e);
        }
    }

    public LeaderboardDTO getLeaderboard(Long quizId, Long requestingUserId) {
        return getLeaderboard(quizId, requestingUserId, "solo");
    }

    private boolean shouldUpdateLeaderboardEntry(String usersKey, String userIdStr, long score,
                                                 int accuracyPercent, int attemptNumber, long timeSpent) {
        Object raw = redisTemplate.opsForHash().get(usersKey, userIdStr);
        if (raw == null) {
            return true;
        }
        try {
            String[] parts = raw.toString().split(":");
            if (parts.length < 3) {
                return true;
            }
            long currentScore = Long.parseLong(parts[1]);
            long currentTime = Long.parseLong(parts[2]);
            int currentAccuracy = parts.length >= 4 && !parts[3].isBlank()
                    ? Integer.parseInt(parts[3])
                    : 0;
            int currentAttemptNumber = parts.length >= 5 && !parts[4].isBlank()
                    ? Integer.parseInt(parts[4])
                    : Integer.MAX_VALUE;

            if (score != currentScore) {
                return score > currentScore;
            }
            if (accuracyPercent != currentAccuracy) {
                return accuracyPercent > currentAccuracy;
            }
            if (attemptNumber != currentAttemptNumber) {
                return attemptNumber < currentAttemptNumber;
            }
            return timeSpent > 0 && (currentTime <= 0 || timeSpent < currentTime);
        } catch (RuntimeException e) {
            return true;
        }
    }

    public LeaderboardDTO getLeaderboard(Long quizId, Long requestingUserId, String mode) {
        try {
            String normalizedMode = normalizeMode(mode);
            String zsetKey = String.format(ZSET_KEY, quizId, normalizedMode);
            String usersKey = String.format(USERS_KEY, quizId, normalizedMode);

            Set<String> userIds = redisTemplate.opsForZSet().reverseRange(zsetKey, 0, 99);
            if (userIds == null || userIds.isEmpty()) {
                return null;
            }

            List<LeaderboardEntry> entries = new ArrayList<>();
            int userPosition = -1;
            Integer userScore = null;
            int position = 1;

            for (String userIdStr : userIds) {
                Object raw = redisTemplate.opsForHash().get(usersKey, userIdStr);
                if (raw == null) continue;

                String[] parts = raw.toString().split(":");
                if (parts.length < 3) continue;
                if (parts.length < 5 || parts[3].isBlank() || parts[4].isBlank()) {
                    return null;
                }

                String login = parts[0];
                int score = Integer.parseInt(parts[1]);
                long time = Long.parseLong(parts[2]);
                Integer accuracyPercent = Integer.parseInt(parts[3]);
                Integer attemptNumber = Integer.parseInt(parts[4]);

                entries.add(new LeaderboardEntry(position, login, score, accuracyPercent, attemptNumber, time));

                if (requestingUserId != null && requestingUserId.equals(Long.parseLong(userIdStr))) {
                    userPosition = position;
                    userScore = score;
                }
                position++;
            }

            return new LeaderboardDTO(entries, userPosition, userScore);
        } catch (Exception e) {
            log.debug("Не удалось прочитать лидерборд квиза {}", quizId, e);
            return null;
        }
    }

    public void evict(Long quizId) {
        try {
            redisTemplate.delete(String.format(ZSET_KEY, quizId, "solo"));
            redisTemplate.delete(String.format(USERS_KEY, quizId, "solo"));
            redisTemplate.delete(String.format(ZSET_KEY, quizId, "multiplayer"));
            redisTemplate.delete(String.format(USERS_KEY, quizId, "multiplayer"));
        } catch (Exception e) {
            log.debug("Не удалось очистить лидерборд квиза {}", quizId, e);
        }
    }
    private String normalizeMode(String mode) {
        return "multiplayer".equalsIgnoreCase(mode) ? "multiplayer" : "solo";
    }
}

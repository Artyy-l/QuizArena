package org.example.dto.common;

public record LeaderboardEntry(Integer position, String username, Integer score,
                               Integer accuracyPercent, Integer attemptNumber, Long timeSpent) {
    public String formattedTimeSpent() {
        if (timeSpent == null) {
            return "-";
        }
        long totalSeconds = Math.max(0L, timeSpent);
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}

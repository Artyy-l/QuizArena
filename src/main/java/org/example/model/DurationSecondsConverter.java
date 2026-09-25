package org.example.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.Duration;

@Converter(autoApply = false)
public class DurationSecondsConverter implements AttributeConverter<Duration, Integer> {

    @Override
    public Integer convertToDatabaseColumn(Duration duration) {
        if (duration == null) {
            return null;
        }
        return (int) duration.getSeconds();
    }

    @Override
    public Duration convertToEntityAttribute(Integer seconds) {
        if (seconds == null) {
            return null;
        }
        return Duration.ofSeconds(seconds);
    }
}

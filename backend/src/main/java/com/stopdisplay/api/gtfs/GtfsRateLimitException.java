package com.stopdisplay.api.gtfs;

import java.io.IOException;
import java.net.http.HttpHeaders;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

final class GtfsRateLimitException extends IOException {
    private final Duration retryAfter;

    GtfsRateLimitException(String message, HttpHeaders headers) {
        super(message);
        this.retryAfter = parseRetryAfter(headers);
    }

    Duration retryAfter() {
        return retryAfter;
    }

    private Duration parseRetryAfter(HttpHeaders headers) {
        return headers.firstValue("Retry-After")
                .map(value -> {
                    try {
                        long seconds = Long.parseLong(value);
                        return seconds < 0 ? null : Duration.ofSeconds(seconds);
                    } catch (NumberFormatException exception) {
                        try {
                            Instant retryAt = DateTimeFormatter.RFC_1123_DATE_TIME.parse(value, Instant::from);
                            return Duration.between(Instant.now().truncatedTo(ChronoUnit.SECONDS), retryAt);
                        } catch (DateTimeParseException | ArithmeticException ignored) {
                            return null;
                        }
                    }
                })
                .filter(duration -> !duration.isNegative())
                .orElse(null);
    }
}

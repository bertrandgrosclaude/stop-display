package com.stopdisplay.api.gtfs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpHeaders;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class GtfsRefreshBackoffTest {

    @Test
    void parsesRetryAfterSecondsFromRateLimitResponse() {
        HttpHeaders headers = HttpHeaders.of(Map.of("Retry-After", List.of("120")), (name, value) -> true);

        assertEquals(Duration.ofSeconds(120), new GtfsRateLimitException("HTTP 429", headers).retryAfter());
    }

    @Test
    void parsesRetryAfterHttpDateFromRateLimitResponse() {
        Instant retryAt = Instant.now().plusSeconds(120).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        String retryHeader = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.format(retryAt.atZone(java.time.ZoneOffset.UTC));
        HttpHeaders headers = HttpHeaders.of(Map.of("Retry-After", List.of(retryHeader)), (name, value) -> true);

        Duration retryAfter = new GtfsRateLimitException("HTTP 429", headers).retryAfter();

        assertTrue(retryAfter != null && retryAfter.toSeconds() >= 119 && retryAfter.toSeconds() <= 120);
    }

    @Test
    void appliesExponentialBackoffAndHonorsLongerRetryAfter() {
        GtfsRefreshBackoff backoff = new GtfsRefreshBackoff(Duration.ofSeconds(30), Duration.ofSeconds(90));
        Instant now = Instant.parse("2026-10-07T12:00:00Z");

        assertEquals(Duration.ofSeconds(30), backoff.failed(now, null));
        assertFalse(backoff.canAttempt(now.plusSeconds(29)));
        assertTrue(backoff.canAttempt(now.plusSeconds(30)));
        assertEquals(Duration.ofSeconds(120), backoff.failed(now.plusSeconds(30), Duration.ofSeconds(120)));
        assertFalse(backoff.canAttempt(now.plusSeconds(149)));
        assertTrue(backoff.canAttempt(now.plusSeconds(150)));

        backoff.succeeded();

        assertFalse(backoff.hasFailures());
        assertTrue(backoff.canAttempt(now));
        assertNull(new GtfsRateLimitException("HTTP 429", HttpHeaders.of(Map.of(), (name, value) -> true)).retryAfter());
    }
}

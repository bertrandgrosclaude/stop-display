package com.stopdisplay.api.gtfs;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;

final class GtfsRefreshBackoff {
    private final Duration baseDelay;
    private final Duration maxDelay;
    private int consecutiveFailures;
    private Instant nextAttemptAt = Instant.MIN;

    GtfsRefreshBackoff(Duration baseDelay, Duration maxDelay) {
        this.baseDelay = baseDelay;
        this.maxDelay = maxDelay;
    }

    boolean hasFailures() {
        return consecutiveFailures > 0;
    }

    boolean canAttempt(Instant now) {
        return !now.isBefore(nextAttemptAt);
    }

    Duration failed(Instant now, Duration retryAfter) {
        consecutiveFailures = Math.min(consecutiveFailures + 1, 31);
        int multiplierShift = Math.min(consecutiveFailures - 1, 30);
        Duration delay;
        try {
            delay = baseDelay.multipliedBy(1L << multiplierShift);
        } catch (ArithmeticException exception) {
            delay = maxDelay;
        }
        if (delay.compareTo(maxDelay) > 0) delay = maxDelay;
        if (retryAfter != null && retryAfter.compareTo(delay) > 0) delay = retryAfter;

        try {
            nextAttemptAt = now.plus(delay);
        } catch (ArithmeticException | DateTimeException exception) {
            nextAttemptAt = Instant.MAX;
        }
        return delay;
    }

    void succeeded() {
        consecutiveFailures = 0;
        nextAttemptAt = Instant.MIN;
    }
}

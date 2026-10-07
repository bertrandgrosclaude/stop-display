package com.stopdisplay.api.gtfs;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

record GtfsRealtimeData(Map<StopKey, StopUpdate> stopUpdates, Set<String> cancelledTrips, Instant fetchedAt) {
    static GtfsRealtimeData empty() {
        return new GtfsRealtimeData(Map.of(), Set.of(), Instant.EPOCH);
    }

    boolean isFresh(Instant now, Duration ttl) {
        return !fetchedAt.isBefore(now.minus(ttl)) && !fetchedAt.isAfter(now.plusSeconds(5));
    }

    record StopKey(String tripId, String stopId, int stopSequence) {
    }

    record StopUpdate(Long departureTime, Integer departureDelay, Long arrivalTime, Integer arrivalDelay, boolean skipped) {
    }
}
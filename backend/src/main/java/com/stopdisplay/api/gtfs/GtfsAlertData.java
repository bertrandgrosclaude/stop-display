package com.stopdisplay.api.gtfs;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

record GtfsAlertData(List<AlertRecord> alerts, Instant fetchedAt) {
    static GtfsAlertData empty() {
        return new GtfsAlertData(List.of(), Instant.EPOCH);
    }

    boolean isFresh(Instant now, Duration ttl) {
        return !fetchedAt.isBefore(now.minus(ttl)) && !fetchedAt.isAfter(now.plusSeconds(5));
    }

    record AlertRecord(String id, String title, String message, String effect, Set<String> stopIds, Set<String> routeIds, List<ActivePeriod> periods) {
        boolean appliesTo(Set<String> stationStopIds, Set<String> stationRouteIds) {
            return stopIds.isEmpty() && routeIds.isEmpty()
                    || stopIds.stream().anyMatch(stationStopIds::contains)
                    || routeIds.stream().anyMatch(stationRouteIds::contains);
        }

        boolean isActive(Instant now) {
            return periods.isEmpty() || periods.stream().anyMatch(period -> period.contains(now));
        }

        TransitAlert toTransitAlert() {
            return new TransitAlert(id, title, message, effect);
        }
    }

    record ActivePeriod(Instant startsAt, Instant endsAt) {
        boolean contains(Instant instant) {
            return (startsAt == null || !instant.isBefore(startsAt))
                    && (endsAt == null || instant.isBefore(endsAt));
        }
    }
}
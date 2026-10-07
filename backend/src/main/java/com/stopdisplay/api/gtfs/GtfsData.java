package com.stopdisplay.api.gtfs;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record GtfsData(Map<String, Stop> stops, Map<String, List<ScheduledDeparture>> departuresByStop,
    Map<String, Station> stations, Map<String, Set<String>> routeIdsByStation, LocalDate serviceDate, Set<String> activeServices) {
    public record ScheduledDeparture(String serviceId, String tripId, int stopSequence, String line, String destination, int secondsAfterMidnight) {
    }
}

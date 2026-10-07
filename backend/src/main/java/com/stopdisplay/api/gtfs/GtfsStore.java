package com.stopdisplay.api.gtfs;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class GtfsStore {
    private final GtfsLoader loader = new GtfsLoader();
    private final GtfsRealtimeLoader realtimeLoader = new GtfsRealtimeLoader();
    private final GtfsAlertsLoader alertsLoader = new GtfsAlertsLoader();
    private final AtomicReference<GtfsData> data = new AtomicReference<>(new GtfsData(Map.of(), Map.of(), Map.of(), Map.of(), LocalDate.MIN, java.util.Set.of()));
    private final AtomicReference<GtfsRealtimeData> realtimeData = new AtomicReference<>(GtfsRealtimeData.empty());
    private final AtomicReference<GtfsAlertData> alertData = new AtomicReference<>(GtfsAlertData.empty());
    private final GtfsRefreshBackoff gtfsBackoff;
    private final GtfsRefreshBackoff tripUpdateBackoff;
    private final GtfsRefreshBackoff alertBackoff;
    private final String url;
    private final int departuresLimit;
    private final String tripUpdateUrl;
    private final Duration tripUpdateCacheTtl;
    private final String alertsUrl;
    private final Duration alertsCacheTtl;

    public GtfsStore(@Value("${gtfs.url}") String url, @Value("${gtfs.load-on-startup:true}") boolean loadOnStartup,
            @Value("${gtfs.departures-limit:4}") int departuresLimit, @Value("${gtfs.trip-update.url:}") String tripUpdateUrl,
            @Value("${gtfs.trip-update.cache-ttl-ms:20000}") long tripUpdateCacheTtlMs,
            @Value("${gtfs.alert.url:}") String alertsUrl, @Value("${gtfs.alert.cache-ttl-ms:20000}") long alertsCacheTtlMs,
            @Value("${gtfs.retry.base-delay-ms:30000}") long retryBaseDelayMs,
            @Value("${gtfs.retry.max-delay-ms:900000}") long retryMaxDelayMs) {
        this.url = url;
        this.departuresLimit = departuresLimit;
        this.tripUpdateUrl = tripUpdateUrl;
        this.tripUpdateCacheTtl = Duration.ofMillis(Math.max(1, tripUpdateCacheTtlMs));
        this.alertsUrl = alertsUrl;
        this.alertsCacheTtl = Duration.ofMillis(Math.max(1, alertsCacheTtlMs));
        Duration retryBaseDelay = Duration.ofMillis(Math.max(1, retryBaseDelayMs));
        Duration retryMaxDelay = Duration.ofMillis(Math.max(retryBaseDelay.toMillis(), retryMaxDelayMs));
        this.gtfsBackoff = new GtfsRefreshBackoff(retryBaseDelay, retryMaxDelay);
        this.tripUpdateBackoff = new GtfsRefreshBackoff(retryBaseDelay, retryMaxDelay);
        this.alertBackoff = new GtfsRefreshBackoff(retryBaseDelay, retryMaxDelay);
        if (loadOnStartup) refresh();
    }

    @Scheduled(fixedDelayString = "${gtfs.refresh-delay-ms:86400000}", initialDelayString = "${gtfs.refresh-delay-ms:86400000}")
    public void scheduledRefresh() {
        refresh();
    }

    @Scheduled(fixedDelayString = "${gtfs.retry.check-delay-ms:10000}", initialDelayString = "${gtfs.retry.check-delay-ms:10000}")
    public void retryFailedGtfs() {
        if (gtfsBackoff.hasFailures()) refreshGtfs();
    }

    public void refresh() {
        refreshGtfs();
        refreshTripUpdates();
        refreshAlerts();
    }

    private synchronized void refreshGtfs() {
        Instant now = Instant.now();
        if (!gtfsBackoff.canAttempt(now)) return;
        try {
            data.set(loader.load(url));
            gtfsBackoff.succeeded();
        } catch (Exception exception) {
            Duration retryDelay = gtfsBackoff.failed(now, retryAfter(exception));
            System.err.println("GTFS refresh failed: " + exception.getMessage() + "; retrying in " + retryDelay.toSeconds() + " seconds");
        }
    }

    @Scheduled(fixedDelayString = "${gtfs.trip-update.refresh-delay-ms:30000}", initialDelayString = "${gtfs.trip-update.initial-delay-ms:${gtfs.trip-update.refresh-delay-ms:30000}}")
    public synchronized void refreshTripUpdates() {
        GtfsData snapshot = data.get();
        if (tripUpdateUrl.isBlank() || snapshot.serviceDate().equals(LocalDate.MIN)) return;
        Instant now = Instant.now();
        if (!tripUpdateBackoff.canAttempt(now)) return;
        try {
            realtimeData.set(realtimeLoader.load(tripUpdateUrl, snapshot.serviceDate()));
            tripUpdateBackoff.succeeded();
        } catch (Exception exception) {
            Duration retryDelay = tripUpdateBackoff.failed(now, retryAfter(exception));
            System.err.println("GTFS-Realtime refresh failed: " + exception.getMessage() + "; retrying in " + retryDelay.toSeconds() + " seconds");
        }
    }

    @Scheduled(fixedDelayString = "${gtfs.alert.refresh-delay-ms:30000}", initialDelayString = "${gtfs.alert.initial-delay-ms:${gtfs.alert.refresh-delay-ms:30000}}")
    public synchronized void refreshAlerts() {
        if (alertsUrl.isBlank()) return;
        Instant now = Instant.now();
        if (!alertBackoff.canAttempt(now)) return;
        try {
            alertData.set(alertsLoader.load(alertsUrl));
            alertBackoff.succeeded();
        } catch (Exception exception) {
            Duration retryDelay = alertBackoff.failed(now, retryAfter(exception));
            System.err.println("GTFS-Realtime alert refresh failed: " + exception.getMessage() + "; retrying in " + retryDelay.toSeconds() + " seconds");
        }
    }

    private Duration retryAfter(Exception exception) {
        return exception instanceof GtfsRateLimitException rateLimitException ? rateLimitException.retryAfter() : null;
    }

    public List<Stop> search(String query, int limit) {
        String normalizedQuery = normalize(query);
        return data.get().stops().values().stream()
                .filter(stop -> normalize(stop.name()).contains(normalizedQuery))
                .sorted(Comparator.comparing(Stop::name))
                .limit(limit)
                .toList();
    }

    public List<Station> searchStations(String query, int limit) {
        String normalizedQuery = normalize(query);
        return data.get().stations().values().stream().filter(station -> normalize(station.name()).contains(normalizedQuery)).sorted(Comparator.comparing(Station::name)).limit(limit).toList();
    }

    public Station findStation(String id) {
        return data.get().stations().get(id);
    }

    public List<TransitAlert> stationAlerts(String stationId) {
        GtfsData snapshot = data.get();
        Station station = snapshot.stations().get(stationId);
        if (station == null) return List.of();
        GtfsAlertData alerts = alertData.get();
        if (!alerts.isFresh(Instant.now(), alertsCacheTtl)) return List.of();
        Set<String> stopIds = station.stops().stream().map(Stop::id).collect(java.util.stream.Collectors.toSet());
        Set<String> routeIds = snapshot.routeIdsByStation().getOrDefault(stationId, Set.of());
        return selectStationAlerts(alerts, stopIds, routeIds, Instant.now());
    }

    static List<TransitAlert> selectStationAlerts(GtfsAlertData alerts, Set<String> stopIds, Set<String> routeIds, Instant now) {
        return alerts.alerts().stream().filter(alert -> alert.appliesTo(stopIds, routeIds) && alert.isActive(now))
                .map(GtfsAlertData.AlertRecord::toTransitAlert).toList();
    }

    public List<StopDepartures> stationDepartures(String stationId, LocalTime from) {
        GtfsData snapshot = data.get();
        Station station = snapshot.stations().get(stationId);
        if (station == null) return List.of();
        GtfsRealtimeData currentRealtime = freshRealtimeData();
        return station.stops().stream().map(stop -> new StopDepartures(stop, mainLineDirection(snapshot, stop.id()),
            selectDepartures(snapshot, stop.id(), from, departuresLimit, currentRealtime))).toList();
    }

    static String mainLineDirection(GtfsData snapshot, String stopId) {
        Map<String, Map<String, Integer>> destinationsByLine = new java.util.HashMap<>();
        for (GtfsData.ScheduledDeparture departure : snapshot.departuresByStop().getOrDefault(stopId, List.of())) {
            if (!snapshot.activeServices().contains(departure.serviceId()) || departure.line().isBlank() || departure.destination().isBlank()) continue;
            destinationsByLine.computeIfAbsent(departure.line(), ignored -> new java.util.HashMap<>())
                    .merge(departure.destination(), 1, Integer::sum);
        }

        String mainLine = "";
        int mainLineDepartures = 0;
        for (Map.Entry<String, Map<String, Integer>> line : destinationsByLine.entrySet()) {
            int lineDepartures = line.getValue().values().stream().mapToInt(Integer::intValue).sum();
            if (lineDepartures > mainLineDepartures || lineDepartures == mainLineDepartures
                    && (mainLine.isBlank() || line.getKey().compareTo(mainLine) < 0)) {
                mainLine = line.getKey();
                mainLineDepartures = lineDepartures;
            }
        }

        String direction = "";
        int directionDepartures = 0;
        for (Map.Entry<String, Integer> destination : destinationsByLine.getOrDefault(mainLine, Map.of()).entrySet()) {
            if (destination.getValue() > directionDepartures || destination.getValue() == directionDepartures
                    && (direction.isBlank() || destination.getKey().compareTo(direction) < 0)) {
                direction = destination.getKey();
                directionDepartures = destination.getValue();
            }
        }
        return direction;
    }

    public Stop findStop(String id) {
        return data.get().stops().get(id);
    }

    public List<Departure> departures(String stopId, LocalTime from, int limit) {
        return selectDepartures(data.get(), stopId, from, limit, freshRealtimeData());
    }

    private GtfsRealtimeData freshRealtimeData() {
        GtfsRealtimeData snapshot = realtimeData.get();
        return snapshot.isFresh(Instant.now(), tripUpdateCacheTtl) ? snapshot : GtfsRealtimeData.empty();
    }

    static List<Departure> selectDepartures(GtfsData snapshot, String stopId, LocalTime from, int limit, GtfsRealtimeData realtime) {
        ZoneId zone = ZoneId.systemDefault();
        Instant reference = LocalDateTime.of(snapshot.serviceDate(), from).atZone(zone).toInstant();
        List<TimedDeparture> upcoming = new ArrayList<>();
        for (GtfsData.ScheduledDeparture scheduled : snapshot.departuresByStop().getOrDefault(stopId, List.of())) {
            if (!snapshot.activeServices().contains(scheduled.serviceId()) || realtime.cancelledTrips().contains(scheduled.tripId())) continue;
            GtfsRealtimeData.StopUpdate update = matchingUpdate(realtime, scheduled, stopId);
            if (update != null && update.skipped()) continue;

            Instant scheduledTime = LocalDateTime.of(snapshot.serviceDate(), LocalTime.MIDNIGHT).plusSeconds(scheduled.secondsAfterMidnight()).atZone(zone).toInstant();
            Instant departureTime = predictedTime(update, scheduledTime);
            boolean isRealtime = update != null && departureTime != null;
            if (departureTime == null) departureTime = scheduledTime;
            if (departureTime.isBefore(reference)) continue;

            long secondsRemaining = Duration.between(reference, departureTime).getSeconds();
            int minutesRemaining = (int) Math.min(Integer.MAX_VALUE, (secondsRemaining + 59) / 60);
            Departure departure = new Departure(scheduled.line(), scheduled.destination(), departureTime.atZone(zone).toLocalTime(), isRealtime, minutesRemaining);
            upcoming.add(new TimedDeparture(departureTime, departure));
        }
        return upcoming.stream().sorted(Comparator.comparing(TimedDeparture::departureTime))
                .limit(limit).map(TimedDeparture::departure).toList();
    }

    private static GtfsRealtimeData.StopUpdate matchingUpdate(GtfsRealtimeData realtime, GtfsData.ScheduledDeparture scheduled, String stopId) {
        GtfsRealtimeData.StopKey exact = new GtfsRealtimeData.StopKey(scheduled.tripId(), stopId, scheduled.stopSequence());
        GtfsRealtimeData.StopUpdate update = realtime.stopUpdates().get(exact);
        if (update == null && scheduled.stopSequence() != 0) {
            update = realtime.stopUpdates().get(new GtfsRealtimeData.StopKey(scheduled.tripId(), stopId, 0));
        }
        if (update == null && scheduled.stopSequence() != 0) {
            update = realtime.stopUpdates().get(new GtfsRealtimeData.StopKey(scheduled.tripId(), "", scheduled.stopSequence()));
        }
        return update;
    }

    private static Instant predictedTime(GtfsRealtimeData.StopUpdate update, Instant scheduledTime) {
        if (update == null) return null;
        if (update.departureTime() != null) return Instant.ofEpochSecond(update.departureTime());
        if (update.departureDelay() != null) return scheduledTime.plusSeconds(update.departureDelay());
        if (update.arrivalTime() != null) return Instant.ofEpochSecond(update.arrivalTime());
        if (update.arrivalDelay() != null) return scheduledTime.plusSeconds(update.arrivalDelay());
        return null;
    }

    private record TimedDeparture(Instant departureTime, Departure departure) {
    }

    private String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }
}

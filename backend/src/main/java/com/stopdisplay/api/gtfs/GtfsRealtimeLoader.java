package com.stopdisplay.api.gtfs;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.transit.realtime.GtfsRealtime;

final class GtfsRealtimeLoader {
    private static final DateTimeFormatter GTFS_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    GtfsRealtimeData load(String url, LocalDate serviceDate) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) throw new IOException("GTFS-Realtime download failed: HTTP " + response.statusCode());

        GtfsRealtime.FeedMessage feed = GtfsRealtime.FeedMessage.parseFrom(response.body());
        Map<GtfsRealtimeData.StopKey, GtfsRealtimeData.StopUpdate> stopUpdates = new HashMap<>();
        Set<String> cancelledTrips = new HashSet<>();
        String expectedDate = serviceDate.format(GTFS_DATE);

        for (GtfsRealtime.FeedEntity entity : feed.getEntityList()) {
            if (!entity.hasTripUpdate()) continue;
            GtfsRealtime.TripUpdate tripUpdate = entity.getTripUpdate();
            if (!tripUpdate.hasTrip()) continue;
            GtfsRealtime.TripDescriptor trip = tripUpdate.getTrip();
            String tripId = trip.getTripId();
            if (tripId.isBlank() || trip.hasStartDate() && !expectedDate.equals(trip.getStartDate())) continue;
                if (trip.getScheduleRelationship() == GtfsRealtime.TripDescriptor.ScheduleRelationship.CANCELED) {
                cancelledTrips.add(tripId);
                continue;
            }

            for (GtfsRealtime.TripUpdate.StopTimeUpdate stopTime : tripUpdate.getStopTimeUpdateList()) {
                String stopId = stopTime.hasStopId() ? stopTime.getStopId() : "";
                int stopSequence = stopTime.hasStopSequence() ? stopTime.getStopSequence() : 0;
                if (stopId.isBlank() && stopSequence == 0) continue;

                Long departureTime = eventTime(stopTime, true);
                Integer departureDelay = eventDelay(stopTime, true);
                Long arrivalTime = eventTime(stopTime, false);
                Integer arrivalDelay = eventDelay(stopTime, false);
                boolean skipped = stopTime.getScheduleRelationship() == GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.SKIPPED;
                if (!skipped && departureTime == null && departureDelay == null && arrivalTime == null && arrivalDelay == null) continue;

                stopUpdates.put(new GtfsRealtimeData.StopKey(tripId, stopId, stopSequence),
                        new GtfsRealtimeData.StopUpdate(departureTime, departureDelay, arrivalTime, arrivalDelay, skipped));
            }
        }

        return new GtfsRealtimeData(Map.copyOf(stopUpdates), Set.copyOf(cancelledTrips), Instant.now());
    }

    private Long eventTime(GtfsRealtime.TripUpdate.StopTimeUpdate update, boolean departure) {
        if (departure ? !update.hasDeparture() : !update.hasArrival()) return null;
        GtfsRealtime.TripUpdate.StopTimeEvent event = departure ? update.getDeparture() : update.getArrival();
        return event.hasTime() ? event.getTime() : null;
    }

    private Integer eventDelay(GtfsRealtime.TripUpdate.StopTimeUpdate update, boolean departure) {
        if (departure ? !update.hasDeparture() : !update.hasArrival()) return null;
        GtfsRealtime.TripUpdate.StopTimeEvent event = departure ? update.getDeparture() : update.getArrival();
        return event.hasDelay() ? event.getDelay() : null;
    }
}
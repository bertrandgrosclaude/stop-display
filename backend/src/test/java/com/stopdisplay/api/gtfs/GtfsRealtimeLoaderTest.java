package com.stopdisplay.api.gtfs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import com.google.transit.realtime.GtfsRealtime;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

class GtfsRealtimeLoaderTest {

    @Test
    void loadsTripUpdateForMatchingTripAndStopAndExpiresItQuickly() throws Exception {
        long predictedTime = Instant.parse("2026-09-30T10:15:00Z").getEpochSecond();
        byte[] fixture = GtfsRealtime.FeedMessage.newBuilder()
                .setHeader(GtfsRealtime.FeedHeader.newBuilder().setGtfsRealtimeVersion("2.0"))
                .addEntity(GtfsRealtime.FeedEntity.newBuilder().setId("update-1")
                        .setTripUpdate(GtfsRealtime.TripUpdate.newBuilder()
                                .setTrip(GtfsRealtime.TripDescriptor.newBuilder().setTripId("trip-1").setStartDate("20260930"))
                                .addStopTimeUpdate(GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder()
                                        .setStopId("41133")
                                        .setStopSequence(3)
                                        .setDeparture(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setTime(predictedTime).setDelay(120)))))
                .build().toByteArray();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/TripUpdate.pb", exchange -> {
            exchange.sendResponseHeaders(200, fixture.length);
            try (var output = exchange.getResponseBody()) {
                output.write(fixture);
            }
        });
        server.start();

        try {
            GtfsRealtimeData data = new GtfsRealtimeLoader().load(
                    "http://localhost:" + server.getAddress().getPort() + "/TripUpdate.pb", LocalDate.of(2026, 9, 30));

            var update = data.stopUpdates().get(new GtfsRealtimeData.StopKey("trip-1", "41133", 3));
            assertEquals(predictedTime, update.departureTime());
            assertEquals(120, update.departureDelay());
            assertTrue(data.isFresh(data.fetchedAt().plusSeconds(19), Duration.ofSeconds(20)));
            assertFalse(data.isFresh(data.fetchedAt().plusSeconds(21), Duration.ofSeconds(20)));
        } finally {
            server.stop(0);
        }
    }
}
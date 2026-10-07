package com.stopdisplay.api.gtfs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import com.google.transit.realtime.GtfsRealtime;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

class GtfsAlertsLoaderTest {

    @Test
    void loadsFrenchAlertTextAndStopAndRouteScopes() throws Exception {
        long start = Instant.now().minusSeconds(60).getEpochSecond();
        long end = Instant.now().plusSeconds(600).getEpochSecond();
        byte[] fixture = GtfsRealtime.FeedMessage.newBuilder()
                .setHeader(GtfsRealtime.FeedHeader.newBuilder().setGtfsRealtimeVersion("2.0"))
                .addEntity(GtfsRealtime.FeedEntity.newBuilder().setId("alert-1")
                        .setAlert(GtfsRealtime.Alert.newBuilder()
                                .addActivePeriod(GtfsRealtime.TimeRange.newBuilder().setStart(start).setEnd(end))
                                .addInformedEntity(GtfsRealtime.EntitySelector.newBuilder().setStopId("41133"))
                                .addInformedEntity(GtfsRealtime.EntitySelector.newBuilder().setRouteId("1"))
                                .setHeaderText(translated("fr", "Travaux"))
                                .setDescriptionText(translated("en", "Diversion").addTranslation(
                                        GtfsRealtime.TranslatedString.Translation.newBuilder().setLanguage("fr").setText("Arrêt déplacé")))
                                .setEffect(GtfsRealtime.Alert.Effect.DETOUR)))
                .build().toByteArray();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/Alert.pb", exchange -> {
            exchange.sendResponseHeaders(200, fixture.length);
            try (var output = exchange.getResponseBody()) {
                output.write(fixture);
            }
        });
        server.start();

        try {
            GtfsAlertData data = new GtfsAlertsLoader().load("http://localhost:" + server.getAddress().getPort() + "/Alert.pb");
            var alert = data.alerts().getFirst();

            assertEquals("Travaux", alert.title());
            assertEquals("Arrêt déplacé", alert.message());
            assertEquals("DETOUR", alert.effect());
            assertTrue(alert.appliesTo(Set.of("41133"), Set.of()));
            assertTrue(alert.appliesTo(Set.of(), Set.of("1")));
            assertTrue(alert.isActive(Instant.now()));
            assertTrue(data.isFresh(data.fetchedAt().plusSeconds(19), Duration.ofSeconds(20)));
            assertFalse(data.isFresh(data.fetchedAt().plusSeconds(21), Duration.ofSeconds(20)));
        } finally {
            server.stop(0);
        }
    }

    private GtfsRealtime.TranslatedString.Builder translated(String language, String text) {
        return GtfsRealtime.TranslatedString.newBuilder().addTranslation(
                GtfsRealtime.TranslatedString.Translation.newBuilder().setLanguage(language).setText(text));
    }
}
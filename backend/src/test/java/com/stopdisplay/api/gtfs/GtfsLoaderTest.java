package com.stopdisplay.api.gtfs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

class GtfsLoaderTest {

    @Test
    void loadsStaticMontpellierGtfsArchiveAndLinksDeparturesToTrips() throws Exception {
        byte[] fixture = readFixture();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/GTFS.zip", exchange -> {
            exchange.sendResponseHeaders(200, fixture.length);
            try (var output = exchange.getResponseBody()) {
                output.write(fixture);
            }
        });
        server.start();

        try {
            String url = "http://localhost:" + server.getAddress().getPort() + "/GTFS.zip";
            GtfsData data = new GtfsLoader().load(url);

            Stop gareSaintRoch = data.stops().get("41135");
            assertNotNull(gareSaintRoch);
            assertEquals("Gare Saint-Roch", gareSaintRoch.name());
            assertFalse(data.stops().isEmpty());

            var departures = data.departuresByStop().get("41135");
            assertNotNull(departures);
            assertFalse(departures.isEmpty());
            assertFalse(departures.get(0).line().isBlank());
            assertFalse(departures.get(0).destination().isBlank());
            assertTrue(departures.get(0).secondsAfterMidnight() <= departures.get(1).secondsAfterMidnight());

            Station comedie = data.stations().values().stream().filter(station -> "Comédie".equals(station.name())).findFirst().orElse(null);
            assertNotNull(comedie);
            assertEquals("Comédie", comedie.name());
            assertEquals(List.of("41133", "41223"), comedie.stops().stream().map(Stop::id).toList());
            assertTrue(comedie.lines().contains(new StationLine("1", "#005CA9", "#FFFFFF")));
            assertEquals("name:comedie", data.stops().get("41133").parentStation());
            assertEquals("name:comedie", data.stops().get("41223").parentStation());
        } finally {
            server.stop(0);
        }
    }

    private byte[] readFixture() throws IOException {
        try (var input = getClass().getResourceAsStream("/gtfs/GTFS.zip")) {
            assertNotNull(input, "GTFS test fixture is missing");
            return input.readAllBytes();
        }
    }
}

package com.stopdisplay.api.gtfs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import com.google.transit.realtime.GtfsRealtime;
import org.junit.jupiter.api.Test;

class GtfsRealtimeInspectorTest {
    private final GtfsRealtimeInspector inspector = new GtfsRealtimeInspector("gtfsproxy.e-tam.fr");

    @Test
    void decodesFeedHeaderAndEntityContents() throws Exception {
        byte[] protobuf = GtfsRealtime.FeedMessage.newBuilder()
                .setHeader(GtfsRealtime.FeedHeader.newBuilder().setGtfsRealtimeVersion("2.0").setTimestamp(1234))
                .addEntity(GtfsRealtime.FeedEntity.newBuilder().setId("trip-update-1")
                        .setTripUpdate(GtfsRealtime.TripUpdate.newBuilder()
                                .setTrip(GtfsRealtime.TripDescriptor.newBuilder().setTripId("trip-42"))))
                .build().toByteArray();

        GtfsRealtimeInspection result = inspector.parse("https://gtfsproxy.e-tam.fr/COMMON/TripUpdate.pb", protobuf, 20);

        assertEquals("2.0", result.version());
        assertEquals(1234L, result.timestamp());
        assertEquals(1, result.totalEntityCount());
        assertEquals("trip-update-1", result.entities().getFirst().id());
        Map<?, ?> tripUpdate = (Map<?, ?>) result.entities().getFirst().content().get("tripUpdate");
        Map<?, ?> trip = (Map<?, ?>) tripUpdate.get("trip");
        assertEquals("trip-42", trip.get("tripId"));
    }

    @Test
    void rejectsHttpForeignHostsAndNonProtobufPaths() {
        assertThrows(IllegalArgumentException.class, () -> inspector.validateUrl("http://gtfsproxy.e-tam.fr/COMMON/Alert.pb"));
        assertThrows(IllegalArgumentException.class, () -> inspector.validateUrl("https://example.com/COMMON/Alert.pb"));
        assertThrows(IllegalArgumentException.class, () -> inspector.validateUrl("https://gtfsproxy.e-tam.fr/COMMON/GTFS.zip"));
    }
}
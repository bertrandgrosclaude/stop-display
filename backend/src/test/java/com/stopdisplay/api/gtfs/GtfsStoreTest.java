package com.stopdisplay.api.gtfs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class GtfsStoreTest {

    @Test
    void replacesScheduledTimeWithFreshPredictionAndFallsBackToScheduleWithoutUpdate() {
        LocalDate serviceDate = LocalDate.of(2026, 9, 30);
        LocalTime from = LocalTime.of(10, 0);
        var scheduled = new GtfsData.ScheduledDeparture("service-1", "trip-1", 3, "1", "Mosson", 11 * 60 * 60);
        GtfsData data = new GtfsData(Map.of(), Map.of("41133", List.of(scheduled)), Map.of(), Map.of(), serviceDate, Set.of("service-1"));
        Instant prediction = serviceDate.atTime(10, 10).atZone(ZoneId.systemDefault()).toInstant();
        GtfsRealtimeData realtime = new GtfsRealtimeData(
                Map.of(new GtfsRealtimeData.StopKey("trip-1", "41133", 3),
                        new GtfsRealtimeData.StopUpdate(prediction.getEpochSecond(), null, null, null, false)),
                Set.of(), Instant.now());

        Departure predictedDeparture = GtfsStore.selectDepartures(data, "41133", from, 4, realtime).getFirst();
        Departure scheduledDeparture = GtfsStore.selectDepartures(data, "41133", from, 4, GtfsRealtimeData.empty()).getFirst();

        assertTrue(predictedDeparture.realtime());
        assertEquals(LocalTime.of(10, 10), predictedDeparture.time());
        assertEquals(10, predictedDeparture.minutesRemaining());
        assertFalse(scheduledDeparture.realtime());
        assertEquals(LocalTime.of(11, 0), scheduledDeparture.time());
        assertEquals(60, scheduledDeparture.minutesRemaining());
    }

    @Test
    void selectsStopRouteAndGlobalAlertsForStation() {
        Instant now = Instant.now();
        GtfsAlertData alerts = new GtfsAlertData(List.of(
                alert("stop-alert", Set.of("41133"), Set.of()),
                alert("route-alert", Set.of(), Set.of("route-1")),
                alert("other-stop", Set.of("other-stop"), Set.of()),
                alert("global-alert", Set.of(), Set.of())), now);

        List<TransitAlert> selected = GtfsStore.selectStationAlerts(alerts, Set.of("41133", "41223"), Set.of("route-1"), now);

        assertEquals(List.of("stop-alert", "route-alert", "global-alert"), selected.stream().map(TransitAlert::id).toList());
    }

    @Test
    void findsMostFrequentDestinationOnTheMainActiveLine() {
        List<GtfsData.ScheduledDeparture> scheduled = List.of(
            scheduled("Mosson", "1"), scheduled("Mosson", "1"), scheduled("Mosson", "1"),
            scheduled("Sabines", "2"), scheduled("Sabines", "2"));
        GtfsData data = new GtfsData(Map.of(), Map.of("41133", scheduled), Map.of(), Map.of(),
                LocalDate.of(2026, 9, 30), Set.of("service-1"));

        assertEquals("Mosson", GtfsStore.mainLineDirection(data, "41133"));
    }

    private GtfsAlertData.AlertRecord alert(String id, Set<String> stopIds, Set<String> routeIds) {
        return new GtfsAlertData.AlertRecord(id, id, "Message " + id, "UNKNOWN_EFFECT", stopIds, routeIds, List.of());
    }

    private GtfsData.ScheduledDeparture scheduled(String destination, String line) {
        return new GtfsData.ScheduledDeparture("service-1", destination + line, 1, line, destination, 36000);
    }
}
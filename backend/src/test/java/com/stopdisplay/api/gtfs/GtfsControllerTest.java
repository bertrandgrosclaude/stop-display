package com.stopdisplay.api.gtfs;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class GtfsControllerTest {
    private static final Stop STOP = new Stop("41135", "41135", "Gare Saint-Roch", 43.60575, 3.88008, "STOP");
    private final GtfsStore store = mock(GtfsStore.class);
    private final GtfsRealtimeInspector inspector = mock(GtfsRealtimeInspector.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new GtfsController(store), new StationController(store), new GtfsRealtimeInspectionController(inspector)).build();
    }

    @Test
    void searchesStopsAndReturnsJsonResults() throws Exception {
        when(store.search("gare", 20)).thenReturn(List.of(STOP));

        mockMvc.perform(get("/api/stops/search").param("q", "gare"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$[0].id").value("41135"))
                .andExpect(jsonPath("$[0].name").value("Gare Saint-Roch"));
    }

    @Test
    void doesNotSearchWhenQueryIsTooShort() throws Exception {
        mockMvc.perform(get("/api/stops/search").param("q", "g"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void returnsStopDetails() throws Exception {
        when(store.findStop("41135")).thenReturn(STOP);

        mockMvc.perform(get("/api/stops/{id}", "41135"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("41135"))
                .andExpect(jsonPath("$.type").value("STOP"));
    }

    @Test
    void searchResultsIncludeBothLineColors() throws Exception {
        Station station = new Station("name:comedie", "Comédie", 43.61, 3.88, List.of(STOP), List.of(new StationLine("1", "#005CA9", "#FFFFFF")));
        when(store.searchStations("comedie", 20)).thenReturn(List.of(station));

        mockMvc.perform(get("/api/stations/search").param("q", "comedie"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lines[0].name").value("1"))
                .andExpect(jsonPath("$[0].lines[0].color").value("#005CA9"))
                .andExpect(jsonPath("$[0].lines[0].textColor").value("#FFFFFF"));
    }

    @Test
    void returnsDeparturesAndPassesFromTimeToStore() throws Exception {
        when(store.findStop("41135")).thenReturn(STOP);
        when(store.departures(eq("41135"), eq(LocalTime.of(5, 0)), eq(10)))
                .thenReturn(List.of(new Departure("1", "Gare Sud France", LocalTime.of(5, 5))));

        mockMvc.perform(get("/api/stops/{id}/departures", "41135").param("from", "05:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].line").value("1"))
                .andExpect(jsonPath("$[0].destination").value("Gare Sud France"))
                .andExpect(jsonPath("$[0].time").value("05:05:00"));
    }

            @Test
            void returnsRealtimeStatusAndMinutesRemainingForStationDepartures() throws Exception {
            Station station = new Station("station-1", "Gare Saint-Roch", 43.60, 3.88, List.of(STOP), List.of());
                Stop describedStop = new Stop(STOP.id(), STOP.code(), STOP.name(), STOP.latitude(), STOP.longitude(), STOP.type(), "", "Quai A");
            when(store.findStation("station-1")).thenReturn(station);
            when(store.stationDepartures(eq("station-1"), any(LocalTime.class)))
                    .thenReturn(List.of(new StopDepartures(describedStop, "Mosson", List.of(new Departure("1", "Mosson", LocalTime.of(10, 5), true, 5)))));

            mockMvc.perform(get("/api/stations/{id}/departures", "station-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stop.description").value("Quai A"))
                .andExpect(jsonPath("$[0].mainLineDirection").value("Mosson"))
                .andExpect(jsonPath("$[0].departures[0].realtime").value(true))
                .andExpect(jsonPath("$[0].departures[0].minutesRemaining").value(5));
            }

    @Test
    void returnsStationInformationMessages() throws Exception {
        when(store.findStation("station-1")).thenReturn(new Station("station-1", "Gare Saint-Roch", 43.60, 3.88, List.of(STOP), List.of()));
        when(store.stationAlerts("station-1")).thenReturn(List.of(new TransitAlert("alert-1", "Travaux", "Arrêt déplacé", "DETOUR")));

        mockMvc.perform(get("/api/stations/{id}/alerts", "station-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("alert-1"))
                .andExpect(jsonPath("$[0].title").value("Travaux"))
                .andExpect(jsonPath("$[0].message").value("Arrêt déplacé"))
                .andExpect(jsonPath("$[0].effect").value("DETOUR"));
    }

            @Test
            void inspectsProtobufUrlAndReturnsReadableFeedContent() throws Exception {
            String url = "https://gtfsproxy.e-tam.fr/COMMON/TripUpdate.pb";
            java.util.Map<String, Object> content = java.util.Map.of("tripUpdate",
                java.util.Map.of("trip", java.util.Map.of("tripId", "trip-42")));
            var entity = new GtfsRealtimeInspection.EntityContent("trip-update-1", content);
            when(inspector.inspect(url, 20)).thenReturn(new GtfsRealtimeInspection(url, "2.0", "FULL_DATASET", 1234L,
                1, 1, false, List.of(entity)));

            mockMvc.perform(get("/api/gtfs/realtime/inspect").param("url", url))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("2.0"))
                .andExpect(jsonPath("$.totalEntityCount").value(1))
                .andExpect(jsonPath("$.entities[0].id").value("trip-update-1"))
                .andExpect(jsonPath("$.entities[0].content.tripUpdate.trip.tripId").value("trip-42"));
            }

            @Test
            void rejectsUnsafeProtobufUrl() throws Exception {
            String url = "http://localhost/private.pb";
            doThrow(new IllegalArgumentException("url must be an HTTPS .pb resource on an allowed host"))
                .when(inspector).inspect(url, 20);

            mockMvc.perform(get("/api/gtfs/realtime/inspect").param("url", url))
                .andExpect(status().isBadRequest());
            }

    @Test
    void returnsNotFoundForUnknownStop() throws Exception {
        when(store.findStop("unknown")).thenReturn(null);

        mockMvc.perform(get("/api/stops/{id}", "unknown"))
                .andExpect(status().isNotFound());
    }

    @Test
    void acceptsManualRefresh() throws Exception {
        mockMvc.perform(post("/api/stops/refresh"))
                .andExpect(status().isAccepted());

        verify(store).refresh();
    }
}

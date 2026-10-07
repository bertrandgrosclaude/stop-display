package com.stopdisplay.api.gtfs;

import java.time.LocalTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/stations")
public class StationController {
    private final GtfsStore store;

    public StationController(GtfsStore store) {
        this.store = store;
    }

    @GetMapping("/search")
    public List<Station> search(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "20") int limit) {
        if (q.trim().length() < 2) return List.of();
        return store.searchStations(q, Math.min(Math.max(limit, 1), 50));
    }

    @GetMapping("/{id}")
    public Station station(@PathVariable String id) {
        Station station = store.findStation(id);
        if (station == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Station not found");
        return station;
    }

    @GetMapping("/{id}/departures")
    public List<StopDepartures> departures(@PathVariable String id, @RequestParam(required = false) LocalTime from) {
        if (store.findStation(id) == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Station not found");
        return store.stationDepartures(id, from == null ? LocalTime.now() : from);
    }

    @GetMapping("/{id}/alerts")
    public List<TransitAlert> alerts(@PathVariable String id) {
        if (store.findStation(id) == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Station not found");
        return store.stationAlerts(id);
    }
}
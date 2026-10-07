package com.stopdisplay.api.gtfs;

import java.time.LocalTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/stops")
public class GtfsController {
    private final GtfsStore store;

    public GtfsController(GtfsStore store) {
        this.store = store;
    }

    @GetMapping("/search")
    public List<Stop> search(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "20") int limit) {
        if (q.trim().length() < 2) return List.of();
        return store.search(q, Math.min(Math.max(limit, 1), 50));
    }

    @GetMapping("/{id}")
    public Stop stop(@PathVariable String id) {
        Stop stop = store.findStop(id);
        if (stop == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Stop not found");
        return stop;
    }

    @GetMapping("/{id}/departures")
    public List<Departure> departures(@PathVariable String id, @RequestParam(required = false) LocalTime from) {
        if (store.findStop(id) == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Stop not found");
        return store.departures(id, from == null ? LocalTime.now() : from, 10);
    }

    @PostMapping("/refresh")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void refresh() {
        store.refresh();
    }
}

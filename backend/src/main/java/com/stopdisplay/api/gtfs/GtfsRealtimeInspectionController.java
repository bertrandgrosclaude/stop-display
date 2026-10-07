package com.stopdisplay.api.gtfs;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/gtfs/realtime")
public class GtfsRealtimeInspectionController {
    private final GtfsRealtimeInspector inspector;

    public GtfsRealtimeInspectionController(GtfsRealtimeInspector inspector) {
        this.inspector = inspector;
    }

    @GetMapping("/inspect")
    public GtfsRealtimeInspection inspect(@RequestParam String url, @RequestParam(defaultValue = "20") int limit) {
        try {
            return inspector.inspect(url, limit);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Feed inspection was interrupted");
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to fetch or decode the protobuf feed");
        }
    }
}
package com.stopdisplay.api.gtfs;

import java.util.List;

public record Station(String id, String name, double latitude, double longitude, List<Stop> stops, List<StationLine> lines) {
}

package com.stopdisplay.api.gtfs;

import java.util.List;
import java.util.Map;

public record GtfsRealtimeInspection(String url, String version, String incrementality, Long timestamp,
        int totalEntityCount, int returnedEntityCount, boolean truncated, List<EntityContent> entities) {
    public record EntityContent(String id, Map<String, Object> content) {
    }
}
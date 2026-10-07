package com.stopdisplay.api.gtfs;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.transit.realtime.GtfsRealtime;

final class GtfsAlertsLoader {
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    GtfsAlertData load(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) throw new IOException("GTFS-Realtime alert download failed: HTTP " + response.statusCode());

        GtfsRealtime.FeedMessage feed = GtfsRealtime.FeedMessage.parseFrom(response.body());
        List<GtfsAlertData.AlertRecord> alerts = new ArrayList<>();
        for (GtfsRealtime.FeedEntity entity : feed.getEntityList()) {
            if (!entity.hasAlert()) continue;
            GtfsRealtime.Alert alert = entity.getAlert();
            String title = translatedText(alert.getHeaderText());
            String message = translatedText(alert.getDescriptionText());
            if (title.isBlank() && message.isBlank()) continue;
            if (title.isBlank()) title = "Information réseau";

            Set<String> stopIds = new HashSet<>();
            Set<String> routeIds = new HashSet<>();
            for (GtfsRealtime.EntitySelector selector : alert.getInformedEntityList()) {
                if (selector.hasStopId() && !selector.getStopId().isBlank()) stopIds.add(selector.getStopId());
                if (selector.hasRouteId() && !selector.getRouteId().isBlank()) routeIds.add(selector.getRouteId());
            }

            List<GtfsAlertData.ActivePeriod> periods = alert.getActivePeriodList().stream()
                    .map(period -> new GtfsAlertData.ActivePeriod(
                            period.hasStart() ? Instant.ofEpochSecond(period.getStart()) : null,
                            period.hasEnd() ? Instant.ofEpochSecond(period.getEnd()) : null))
                    .toList();
            String id = entity.getId().isBlank() ? title : entity.getId();
            alerts.add(new GtfsAlertData.AlertRecord(id, title, message,
                    alert.getEffect().name(), Set.copyOf(stopIds), Set.copyOf(routeIds), periods));
        }
        return new GtfsAlertData(List.copyOf(alerts), Instant.now());
    }

    private String translatedText(GtfsRealtime.TranslatedString translatedString) {
        List<GtfsRealtime.TranslatedString.Translation> translations = translatedString.getTranslationList();
        return translations.stream()
                .filter(translation -> translation.getLanguage().toLowerCase().startsWith("fr"))
                .map(GtfsRealtime.TranslatedString.Translation::getText)
                .filter(text -> !text.isBlank())
                .findFirst()
                .orElseGet(() -> translations.stream().map(GtfsRealtime.TranslatedString.Translation::getText)
                        .filter(text -> !text.isBlank()).findFirst().orElse(""));
    }
}
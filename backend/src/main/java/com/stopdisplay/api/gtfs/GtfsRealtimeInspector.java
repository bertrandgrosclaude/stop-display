package com.stopdisplay.api.gtfs;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import com.google.transit.realtime.GtfsRealtime;

@Service
public class GtfsRealtimeInspector {
    private static final int MAX_RESPONSE_BYTES = 8 * 1024 * 1024;
    private static final int MAX_ENTITIES = 100;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Set<String> allowedHosts;

    public GtfsRealtimeInspector(@Value("${gtfs.realtime.inspect.allowed-hosts:gtfsproxy.e-tam.fr}") String allowedHosts) {
        this.allowedHosts = Arrays.stream(allowedHosts.split(","))
                .map(String::trim).map(host -> host.toLowerCase(Locale.ROOT))
                .filter(host -> !host.isBlank()).collect(Collectors.toUnmodifiableSet());
    }

    public GtfsRealtimeInspection inspect(String url, int requestedLimit) throws IOException, InterruptedException {
        URI uri = validateUrl(url);
        int limit = Math.max(1, Math.min(requestedLimit, MAX_ENTITIES));
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        byte[] payload;
        try (InputStream body = response.body()) {
            if (response.statusCode() != 200) throw new IOException("Feed returned HTTP " + response.statusCode());
            long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            if (contentLength > MAX_RESPONSE_BYTES) throw new IOException("Feed exceeds the 8 MB inspection limit");
            payload = body.readNBytes(MAX_RESPONSE_BYTES + 1);
            if (payload.length > MAX_RESPONSE_BYTES) throw new IOException("Feed exceeds the 8 MB inspection limit");
        }

        return parse(uri.toString(), payload, limit);
        }

        GtfsRealtimeInspection parse(String source, byte[] payload, int requestedLimit) throws IOException {
        int limit = Math.max(1, Math.min(requestedLimit, MAX_ENTITIES));
        GtfsRealtime.FeedMessage feed = GtfsRealtime.FeedMessage.parseFrom(payload);
        List<GtfsRealtimeInspection.EntityContent> entities = feed.getEntityList().stream().limit(limit)
            .map(entity -> new GtfsRealtimeInspection.EntityContent(entity.getId(), messageToJson(entity))).toList();
        GtfsRealtime.FeedHeader header = feed.getHeader();
        return new GtfsRealtimeInspection(source, header.getGtfsRealtimeVersion(), header.getIncrementality().name(),
                header.hasTimestamp() ? header.getTimestamp() : null, feed.getEntityCount(), entities.size(),
                feed.getEntityCount() > entities.size(), entities);
    }

    private Map<String, Object> messageToJson(Message message) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<Descriptors.FieldDescriptor, Object> field : message.getAllFields().entrySet()) {
            result.put(jsonFieldName(field.getKey().getName()), fieldToJson(field.getKey(), field.getValue()));
        }
        return result;
    }

    private Object fieldToJson(Descriptors.FieldDescriptor field, Object value) {
        if (field.isRepeated()) {
            List<Object> result = new ArrayList<>();
            for (Object item : (List<?>) value) result.add(scalarToJson(field, item));
            return result;
        }
        return scalarToJson(field, value);
    }

    private Object scalarToJson(Descriptors.FieldDescriptor field, Object value) {
        return switch (field.getJavaType()) {
            case INT, FLOAT, DOUBLE, BOOLEAN, STRING -> value;
            case LONG -> value.toString();
            case BYTE_STRING -> Base64.getEncoder().encodeToString(((com.google.protobuf.ByteString) value).toByteArray());
            case ENUM -> ((Descriptors.EnumValueDescriptor) value).getName();
            case MESSAGE -> messageToJson((Message) value);
        };
    }

    private String jsonFieldName(String protoFieldName) {
        StringBuilder result = new StringBuilder(protoFieldName.length());
        boolean uppercaseNext = false;
        for (char character : protoFieldName.toCharArray()) {
            if (character == '_') {
                uppercaseNext = true;
            } else {
                result.append(uppercaseNext ? Character.toUpperCase(character) : character);
                uppercaseNext = false;
            }
        }
        return result.toString();
    }

    URI validateUrl(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("url must be a valid HTTPS URL", exception);
        }
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                || !allowedHosts.contains(host.toLowerCase(Locale.ROOT))
                || uri.getPort() != -1 && uri.getPort() != 443
                || uri.getUserInfo() != null || uri.getFragment() != null
                || uri.getPath() == null || !uri.getPath().toLowerCase(Locale.ROOT).endsWith(".pb")) {
            throw new IllegalArgumentException("url must be an HTTPS .pb resource on an allowed host");
        }
        return uri;
    }
}
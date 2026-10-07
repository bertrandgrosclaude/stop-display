package com.stopdisplay.api.gtfs;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.onebusaway.gtfs.model.Route;
import org.onebusaway.gtfs.model.ServiceCalendarDate;
import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.onebusaway.gtfs.serialization.GtfsReader;
import org.onebusaway.gtfs.services.GtfsDao;

final class GtfsLoader {
    private static final Set<String> REQUIRED = Set.of("stops.txt", "routes.txt", "trips.txt", "stop_times.txt", "calendar_dates.txt");
    private final HttpClient httpClient = HttpClient.newHttpClient();

    GtfsData load(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            try (InputStream body = response.body()) {
                if (response.statusCode() == 429) {
                    throw new GtfsRateLimitException("GTFS download failed: HTTP 429", response.headers());
                }
                throw new IOException("GTFS download failed: HTTP " + response.statusCode());
            }
        }

        Path directory = Files.createTempDirectory("stop-display-gtfs-");
        try (InputStream body = response.body()) {
            extractArchive(body, directory);
            return convert(directory, LocalDate.now());
        } finally {
            deleteDirectory(directory);
        }
    }

    private GtfsData convert(Path directory, LocalDate serviceDate) throws IOException {
        validateRequiredFiles(directory);
        GtfsReader reader = new GtfsReader();
        reader.setInputLocation(directory.toFile());
        reader.run();
        GtfsDao dao = (GtfsDao) reader.getEntityStore();

        Map<String, Stop> stops = new HashMap<>();
        dao.getAllStops().forEach(stop -> stops.put(stop.getId().getId(), new Stop(
                stop.getId().getId(), stop.getCode(), stop.getName(), stop.getLat(), stop.getLon(),
                stop.getLocationType() == org.onebusaway.gtfs.model.Stop.LOCATION_TYPE_STATION ? "STATION" : "STOP",
                stop.getParentStation() == null ? "" : stop.getParentStation(),
                stop.getDesc() == null ? "" : stop.getDesc())));
        Map<String, Stop> linkedStops = assignInferredParentStations(stops);

        Map<String, String> routes = new HashMap<>();
        Map<String, StationLine> routeLines = new HashMap<>();
        for (Route route : dao.getAllRoutes()) {
            String routeId = route.getId().getId();
            routes.put(routeId, route.getShortName());
            String lineName = route.getShortName();
            if (lineName == null || lineName.isBlank()) lineName = routeId;
            routeLines.put(routeId, new StationLine(lineName, normalizeRouteColor(route.getColor()), normalizeRouteColor(route.getTextColor())));
        }

        Set<String> activeServices = activeServices(dao, serviceDate);
        Map<String, List<GtfsData.ScheduledDeparture>> departures = new HashMap<>();
        Map<String, Set<StationLine>> linesByStation = new HashMap<>();
        Map<String, Set<String>> routeIdsByStation = new HashMap<>();
        for (StopTime stopTime : dao.getAllStopTimes()) {
            if (!(stopTime.getStop() instanceof org.onebusaway.gtfs.model.Stop stop)) continue;
            Trip trip = stopTime.getTrip();
            if (trip == null || stopTime.getDepartureTime() < 0) continue;
            String line = trip.getRoute() == null ? "" : routes.getOrDefault(trip.getRoute().getId().getId(), trip.getRoute().getShortName());
            departures.computeIfAbsent(stop.getId().getId(), ignored -> new ArrayList<>()).add(
                    new GtfsData.ScheduledDeparture(trip.getServiceId().getId(), trip.getId().getId(), stopTime.getStopSequence(), line, trip.getTripHeadsign(), stopTime.getDepartureTime()));
            if (trip.getRoute() != null) {
                StationLine stationLine = routeLines.get(trip.getRoute().getId().getId());
                if (stationLine != null) {
                    Stop linkedStop = linkedStops.get(stop.getId().getId());
                    String stationId = linkedStop == null || linkedStop.parentStation().isBlank()
                            ? stop.getId().getId()
                            : linkedStop.parentStation();
                    linesByStation.computeIfAbsent(stationId, ignored -> new HashSet<>()).add(stationLine);
                    routeIdsByStation.computeIfAbsent(stationId, ignored -> new HashSet<>()).add(trip.getRoute().getId().getId());
                }
            }
        }
        departures.replaceAll((stopId, values) -> values.stream().sorted(Comparator.comparingInt(GtfsData.ScheduledDeparture::secondsAfterMidnight)).toList());
        Map<String, Set<String>> immutableRouteIdsByStation = new HashMap<>();
        routeIdsByStation.forEach((stationId, routeIds) -> immutableRouteIdsByStation.put(stationId, Set.copyOf(routeIds)));
        return new GtfsData(Map.copyOf(linkedStops), Map.copyOf(departures), buildStations(linkedStops, linesByStation),
                Map.copyOf(immutableRouteIdsByStation), serviceDate, Set.copyOf(activeServices));
    }

    private Map<String, Stop> assignInferredParentStations(Map<String, Stop> stops) {
        Map<String, List<Stop>> unparentedByName = new HashMap<>();
        stops.values().stream().filter(stop -> stop.parentStation().isBlank())
                .forEach(stop -> unparentedByName.computeIfAbsent(normalizeName(stop.name()), ignored -> new ArrayList<>()).add(stop));

        Map<String, Stop> linkedStops = new HashMap<>(stops);
        unparentedByName.forEach((name, children) -> {
            if (children.size() < 2) return;
            String stationId = "name:" + name;
            children.forEach(stop -> linkedStops.put(stop.id(), new Stop(stop.id(), stop.code(), stop.name(),
                    stop.latitude(), stop.longitude(), stop.type(), stationId, stop.description())));
        });
        return linkedStops;
    }

    private Set<String> activeServices(GtfsDao dao, LocalDate date) {
        Set<String> active = new HashSet<>();
        for (ServiceCalendarDate calendarDate : dao.getAllCalendarDates()) {
            ServiceDate serviceDate = calendarDate.getDate();
            if (serviceDate.getYear() != date.getYear() || serviceDate.getMonth() != date.getMonthValue() || serviceDate.getDay() != date.getDayOfMonth()) continue;
            if (calendarDate.getExceptionType() == 1) active.add(calendarDate.getServiceId().getId());
            else if (calendarDate.getExceptionType() == 2) active.remove(calendarDate.getServiceId().getId());
        }
        return active;
    }

    private void extractArchive(InputStream input, Path directory) throws IOException {
        Path archive = Files.createTempFile("stop-display-gtfs-", ".zip");
        try (InputStream source = input) {
            Files.copy(source, archive, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            zip.stream().filter(entry -> !entry.isDirectory()).forEach(entry -> extractEntry(zip, entry, directory));
        } finally {
            Files.deleteIfExists(archive);
        }
    }

    private void extractEntry(ZipFile zip, ZipEntry entry, Path directory) {
        try {
            Path target = directory.resolve(entry.getName()).normalize();
            if (!target.startsWith(directory)) throw new IOException("Unsafe GTFS archive entry: " + entry.getName());
            Files.createDirectories(target.getParent());
            try (InputStream input = zip.getInputStream(entry)) {
                Files.copy(input, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new GtfsExtractionException(exception);
        }
    }

    private void validateRequiredFiles(Path directory) throws IOException {
        for (String file : REQUIRED) if (!Files.isRegularFile(directory.resolve(file))) throw new IOException("GTFS archive is missing " + file);
    }

    private Map<String, Station> buildStations(Map<String, Stop> stops, Map<String, Set<StationLine>> linesByStation) {
        Map<String, List<Stop>> childrenByParent = new HashMap<>();
        stops.values().forEach(stop -> { if (!stop.parentStation().isBlank()) childrenByParent.computeIfAbsent(stop.parentStation(), ignored -> new ArrayList<>()).add(stop); });
        Map<String, Station> stations = new HashMap<>();
        stops.values().stream().filter(stop -> "STATION".equals(stop.type())).forEach(station -> stations.put(station.id(), station(station.id(), station.name(), station.latitude(), station.longitude(), childrenByParent.getOrDefault(station.id(), List.of()), linesByStation)));
        childrenByParent.forEach((parentId, children) -> { if (!stations.containsKey(parentId)) stations.put(parentId, stationFromChildren(parentId, children, linesByStation)); });
        Map<String, List<Stop>> unparentedByName = new HashMap<>();
        stops.values().stream().filter(stop -> stop.parentStation().isBlank()).forEach(stop -> unparentedByName.computeIfAbsent(normalizeName(stop.name()), ignored -> new ArrayList<>()).add(stop));
        unparentedByName.forEach((name, children) -> { if (children.size() > 1) stations.putIfAbsent("name:" + name, stationFromChildren("name:" + name, children, linesByStation)); else { Stop stop = children.get(0); stations.putIfAbsent(stop.id(), station(stop.id(), stop.name(), stop.latitude(), stop.longitude(), List.of(stop), linesByStation)); } });
        return Map.copyOf(stations);
    }

    private Station stationFromChildren(String id, List<Stop> children, Map<String, Set<StationLine>> linesByStation) {
        List<Stop> sorted = children.stream().sorted(Comparator.comparing(Stop::id)).toList();
        return station(id, sorted.get(0).name(), sorted.stream().mapToDouble(Stop::latitude).average().orElse(0), sorted.stream().mapToDouble(Stop::longitude).average().orElse(0), sorted, linesByStation);
    }

    private Station station(String id, String name, double latitude, double longitude, List<Stop> stops, Map<String, Set<StationLine>> linesByStation) {
        List<StationLine> lines = linesByStation.getOrDefault(id, Set.of()).stream()
                .sorted(Comparator.comparing(StationLine::name).thenComparing(StationLine::color)).toList();
        return new Station(id, name, latitude, longitude, stops.stream().sorted(Comparator.comparing(Stop::id)).toList(), lines);
    }

    private String normalizeRouteColor(String color) {
        if (color == null || color.isBlank()) return "";
        String hexColor = color.trim().replaceFirst("^#", "");
        return hexColor.matches("(?i)[0-9a-f]{6}") ? "#" + hexColor.toUpperCase(Locale.ROOT) : "";
    }

    private String normalizeName(String value) { return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase().trim(); }

    private void deleteDirectory(Path directory) throws IOException { if (!Files.exists(directory)) return; try (Stream<Path> paths = Files.walk(directory)) { paths.sorted(Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (IOException ignored) { } }); } }

    private static final class GtfsExtractionException extends RuntimeException { GtfsExtractionException(IOException cause) { super(cause); } }
}

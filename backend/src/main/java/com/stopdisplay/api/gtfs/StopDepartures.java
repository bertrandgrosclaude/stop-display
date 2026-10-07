package com.stopdisplay.api.gtfs;

import java.util.List;

public record StopDepartures(Stop stop, String mainLineDirection, List<Departure> departures) {
	public StopDepartures(Stop stop, List<Departure> departures) {
		this(stop, "", departures);
	}
}

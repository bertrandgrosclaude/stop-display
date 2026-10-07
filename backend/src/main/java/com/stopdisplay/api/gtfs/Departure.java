package com.stopdisplay.api.gtfs;

import java.time.LocalTime;

public record Departure(String line, String destination, LocalTime time, boolean realtime, Integer minutesRemaining) {
	public Departure(String line, String destination, LocalTime time) {
		this(line, destination, time, false, null);
	}
}

package com.stopdisplay.api.gtfs;

public record Stop(String id, String code, String name, double latitude, double longitude, String type, String parentStation, String description) {
	public Stop(String id, String code, String name, double latitude, double longitude, String type) {
		this(id, code, name, latitude, longitude, type, "", "");
	}

	public Stop(String id, String code, String name, double latitude, double longitude, String type, String parentStation) {
		this(id, code, name, latitude, longitude, type, parentStation, "");
	}
}

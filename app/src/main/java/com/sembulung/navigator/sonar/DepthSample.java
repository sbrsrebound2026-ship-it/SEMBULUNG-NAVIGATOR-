package com.sembulung.navigator.sonar;

public final class DepthSample {
    public enum Quality { GOOD, QUESTIONABLE, REJECTED }

    public final double lat;
    public final double lon;
    public final double depthMeters;
    public final long timestamp;
    public final Double speedKnots;
    public final Double courseDeg;
    public final Quality quality;
    public final String source;

    public DepthSample(double lat, double lon, double depthMeters, long timestamp,
                       Double speedKnots, Double courseDeg, Quality quality, String source) {
        this.lat = lat;
        this.lon = lon;
        this.depthMeters = depthMeters;
        this.timestamp = timestamp;
        this.speedKnots = speedKnots;
        this.courseDeg = courseDeg;
        this.quality = quality == null ? Quality.GOOD : quality;
        this.source = source == null ? "NMEA" : source;
    }

    public boolean valid() {
        return quality != Quality.REJECTED
                && Double.isFinite(lat) && lat >= -90.0 && lat <= 90.0
                && Double.isFinite(lon) && lon >= -180.0 && lon <= 180.0
                && Double.isFinite(depthMeters) && depthMeters > 0.0 && depthMeters <= 2000.0;
    }
}

package com.sembulung.navigator.sonar;

public final class DepthSample {
    public enum Quality { GOOD, QUESTIONABLE, REJECTED }

    public final double latitude;
    public final double longitude;
    public final double depthMeters;
    public final long timestampMs;
    public final Double speedKnots;
    public final Double courseDegrees;
    public final Quality quality;
    public final String source;

    public DepthSample(double latitude, double longitude, double depthMeters,
                       long timestampMs, Double speedKnots, Double courseDegrees,
                       Quality quality, String source) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.depthMeters = depthMeters;
        this.timestampMs = timestampMs;
        this.speedKnots = speedKnots;
        this.courseDegrees = courseDegrees;
        this.quality = quality == null ? Quality.QUESTIONABLE : quality;
        this.source = source == null ? "UNKNOWN" : source;
    }

    public boolean isUsable() {
        return quality != Quality.REJECTED
                && latitude >= -90.0 && latitude <= 90.0
                && longitude >= -180.0 && longitude <= 180.0
                && depthMeters >= 0.0 && depthMeters <= 2000.0
                && !Double.isNaN(latitude)
                && !Double.isNaN(longitude)
                && !Double.isNaN(depthMeters)
                && !Double.isInfinite(depthMeters);
    }
}

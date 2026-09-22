package com.sembulung.navigator.ais;

public final class AisTarget {
    public final int messageType;
    public final long mmsi;
    public final double latitude;
    public final double longitude;
    public final double speedKnots;
    public final double courseDeg;
    public final Integer headingDeg;
    public final Integer navigationStatus;
    public final long receivedAtMillis;

    public AisTarget(
            int messageType,
            long mmsi,
            double latitude,
            double longitude,
            double speedKnots,
            double courseDeg,
            Integer headingDeg,
            Integer navigationStatus,
            long receivedAtMillis) {
        this.messageType = messageType;
        this.mmsi = mmsi;
        this.latitude = latitude;
        this.longitude = longitude;
        this.speedKnots = speedKnots;
        this.courseDeg = courseDeg;
        this.headingDeg = headingDeg;
        this.navigationStatus = navigationStatus;
        this.receivedAtMillis = receivedAtMillis;
    }

    public boolean hasValidPosition() {
        return !Double.isNaN(latitude)
                && !Double.isNaN(longitude)
                && latitude >= -90.0 && latitude <= 90.0
                && longitude >= -180.0 && longitude <= 180.0;
    }

    public boolean hasMotionVector() {
        return !Double.isNaN(speedKnots)
                && !Double.isNaN(courseDeg)
                && speedKnots >= 0.0
                && courseDeg >= 0.0 && courseDeg < 360.0;
    }
}

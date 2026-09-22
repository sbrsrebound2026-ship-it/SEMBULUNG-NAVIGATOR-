package com.sembulung.navigator;

import android.content.Context;
import android.content.SharedPreferences;

public final class NmeaDataStore {
    private static final String PREFS = "sembulung_nmea_live";

    private NmeaDataStore() {}

    public static void write(
            Context context,
            Double lat,
            Double lon,
            Double heading,
            Double depth,
            Double speed,
            long positionTime,
            long headingTime,
            long depthTime,
            String source) {

        SharedPreferences.Editor e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();

        if (lat != null) e.putString("lat", Double.toString(lat));
        if (lon != null) e.putString("lon", Double.toString(lon));
        if (heading != null) e.putString("heading", Double.toString(heading));
        if (depth != null) e.putString("depth", Double.toString(depth));
        if (speed != null) e.putString("speed", Double.toString(speed));

        e.putLong("position_time", positionTime);
        e.putLong("heading_time", headingTime);
        e.putLong("depth_time", depthTime);
        e.putLong("last_packet_time", System.currentTimeMillis());
        e.putString("source", source == null ? "NMEA" : source);
        e.apply();
    }

    public static Snapshot read(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Snapshot s = new Snapshot();
        s.lat = getDouble(p, "lat");
        s.lon = getDouble(p, "lon");
        s.heading = getDouble(p, "heading");
        s.depth = getDouble(p, "depth");
        s.speed = getDouble(p, "speed");
        s.positionTime = p.getLong("position_time", 0L);
        s.headingTime = p.getLong("heading_time", 0L);
        s.depthTime = p.getLong("depth_time", 0L);
        s.lastPacketTime = p.getLong("last_packet_time", 0L);
        s.source = p.getString("source", "NMEA");
        return s;
    }

    public static void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }

    private static Double getDouble(SharedPreferences p, String key) {
        String raw = p.getString(key, null);
        if (raw == null) return null;
        try { return Double.parseDouble(raw); }
        catch (Exception e) { return null; }
    }

    public static final class Snapshot {
        public Double lat;
        public Double lon;
        public Double heading;
        public Double depth;
        public Double speed;
        public long positionTime;
        public long headingTime;
        public long depthTime;
        public long lastPacketTime;
        public String source;

        public boolean positionFresh(long maxAgeMs) {
            return lat != null && lon != null && fresh(positionTime, maxAgeMs);
        }

        public boolean headingFresh(long maxAgeMs) {
            return heading != null && fresh(headingTime, maxAgeMs);
        }

        public boolean depthFresh(long maxAgeMs) {
            return depth != null && fresh(depthTime, maxAgeMs);
        }

        public boolean packetFresh(long maxAgeMs) {
            return fresh(lastPacketTime, maxAgeMs);
        }

        private boolean fresh(long time, long maxAgeMs) {
            return time > 0 && System.currentTimeMillis() - time <= maxAgeMs;
        }
    }
}

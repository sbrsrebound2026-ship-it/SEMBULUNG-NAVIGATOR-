package com.sembulung.navigator.marine;

import java.util.Locale;

/**
 * Minimal AIS NMEA-0183 decoder for single-fragment AIVDM/AIVDO position reports.
 * Supports message types 1, 2, 3 (Class A) and 18 (Class B).
 */
public final class AisDecoder {
    private AisDecoder() {}

    public static final class PositionReport {
        public final int messageType;
        public final int mmsi;
        public final double latitude;
        public final double longitude;
        public final double sogKnots;
        public final double cogDegrees;
        public final Integer headingDegrees;

        public PositionReport(int messageType, int mmsi, double latitude, double longitude,
                              double sogKnots, double cogDegrees, Integer headingDegrees) {
            this.messageType = messageType;
            this.mmsi = mmsi;
            this.latitude = latitude;
            this.longitude = longitude;
            this.sogKnots = sogKnots;
            this.cogDegrees = cogDegrees;
            this.headingDegrees = headingDegrees;
        }

        public String summary() {
            return String.format(Locale.US,
                    "AIS TYPE %d\nMMSI %09d\n%.6f, %.6f\nSOG %.1f kn • COG %.1f°%s",
                    messageType, mmsi, latitude, longitude, sogKnots, cogDegrees,
                    headingDegrees == null ? "" : " • HDG " + headingDegrees + "°");
        }
    }

    public static PositionReport decodeSingleFragment(String sentence) {
        if (sentence == null) return null;
        String raw = sentence.trim();
        if (!(raw.startsWith("!AIVDM") || raw.startsWith("!AIVDO"))) return null;

        int star = raw.indexOf('*');
        String body = star >= 0 ? raw.substring(0, star) : raw;
        String[] f = body.split(",", -1);
        if (f.length < 7) return null;

        int fragments = parseInt(f[1], -1);
        int fragmentNo = parseInt(f[2], -1);
        if (fragments != 1 || fragmentNo != 1) return null;

        String payload = f[5];
        int fillBits = parseInt(f[6], 0);
        String bits = payloadToBits(payload);
        if (bits == null || bits.length() < 38) return null;
        if (fillBits > 0 && fillBits < bits.length()) bits = bits.substring(0, bits.length() - fillBits);

        int type = (int) unsigned(bits, 0, 6);
        int mmsi = (int) unsigned(bits, 8, 30);

        if (type == 1 || type == 2 || type == 3) {
            if (bits.length() < 137) return null;
            double sog = unsigned(bits, 50, 10) / 10.0;
            double lon = signed(bits, 61, 28) / 600000.0;
            double lat = signed(bits, 89, 27) / 600000.0;
            double cog = unsigned(bits, 116, 12) / 10.0;
            int hdgRaw = (int) unsigned(bits, 128, 9);
            Integer hdg = hdgRaw >= 360 ? null : hdgRaw;
            if (!validPosition(lat, lon)) return null;
            return new PositionReport(type, mmsi, lat, lon, sog >= 102.3 ? 0.0 : sog,
                    cog >= 360.0 ? 0.0 : cog, hdg);
        }

        if (type == 18) {
            if (bits.length() < 133) return null;
            double sog = unsigned(bits, 46, 10) / 10.0;
            double lon = signed(bits, 57, 28) / 600000.0;
            double lat = signed(bits, 85, 27) / 600000.0;
            double cog = unsigned(bits, 112, 12) / 10.0;
            int hdgRaw = (int) unsigned(bits, 124, 9);
            Integer hdg = hdgRaw >= 360 ? null : hdgRaw;
            if (!validPosition(lat, lon)) return null;
            return new PositionReport(type, mmsi, lat, lon, sog >= 102.3 ? 0.0 : sog,
                    cog >= 360.0 ? 0.0 : cog, hdg);
        }

        return null;
    }

    private static boolean validPosition(double lat, double lon) {
        return lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
    }

    private static String payloadToBits(String payload) {
        if (payload == null || payload.isEmpty()) return null;
        StringBuilder out = new StringBuilder(payload.length() * 6);
        for (int i = 0; i < payload.length(); i++) {
            int v = payload.charAt(i) - 48;
            if (v > 40) v -= 8;
            if (v < 0 || v > 63) return null;
            for (int bit = 5; bit >= 0; bit--) out.append(((v >> bit) & 1) == 1 ? '1' : '0');
        }
        return out.toString();
    }

    private static long unsigned(String bits, int start, int len) {
        long v = 0;
        for (int i = start; i < start + len; i++) v = (v << 1) | (bits.charAt(i) == '1' ? 1 : 0);
        return v;
    }

    private static long signed(String bits, int start, int len) {
        long v = unsigned(bits, start, len);
        long sign = 1L << (len - 1);
        return (v & sign) != 0 ? v - (1L << len) : v;
    }

    private static int parseInt(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return fallback; }
    }
}

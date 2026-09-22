package com.sembulung.navigator.ais;

import java.util.Locale;

public final class AisParser {
    private AisParser() {}

    /**
     * Decode single-fragment AIS position reports.
     * Supported message types:
     * 1/2/3 = Class A position report
     * 18    = Class B standard position report
     */
    public static AisTarget parse(String rawSentence) {
        if (rawSentence == null) return null;
        String sentence = rawSentence.trim();
        if (!(sentence.startsWith("!AIVDM") || sentence.startsWith("!AIVDO"))) return null;
        if (!checksumValidIfPresent(sentence)) return null;

        int star = sentence.indexOf('*');
        String body = star >= 0 ? sentence.substring(0, star) : sentence;
        String[] f = body.split(",", -1);
        if (f.length < 7) return null;

        Integer fragments = integer(f[1]);
        Integer fragmentNumber = integer(f[2]);
        if (fragments == null || fragmentNumber == null || fragments != 1 || fragmentNumber != 1) {
            return null; // multi-fragment assembler will be added with static/voyage data.
        }

        String payload = f[5];
        Integer fillBits = integer(f[6]);
        if (payload.isEmpty() || fillBits == null || fillBits < 0 || fillBits > 5) return null;

        BitBuffer bits;
        try {
            bits = BitBuffer.fromPayload(payload, fillBits);
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (bits.length() < 38) return null;

        int type = (int) bits.unsigned(0, 6);
        long mmsi = bits.unsigned(8, 30);
        long now = System.currentTimeMillis();

        if (type == 1 || type == 2 || type == 3) {
            if (bits.length() < 137) return null;
            int navStatus = (int) bits.unsigned(38, 4);
            long sogRaw = bits.unsigned(50, 10);
            long lonRaw = bits.signed(61, 28);
            long latRaw = bits.signed(89, 27);
            long cogRaw = bits.unsigned(116, 12);
            long headingRaw = bits.unsigned(128, 9);

            double lon = coordinate(lonRaw, 180.0);
            double lat = coordinate(latRaw, 90.0);
            double sog = sogRaw == 1023 ? Double.NaN : sogRaw / 10.0;
            double cog = cogRaw >= 3600 ? Double.NaN : cogRaw / 10.0;
            Integer heading = headingRaw == 511 ? null : (int) headingRaw;

            return new AisTarget(type, mmsi, lat, lon, sog, cog, heading, navStatus, now);
        }

        if (type == 18) {
            if (bits.length() < 133) return null;
            long sogRaw = bits.unsigned(46, 10);
            long lonRaw = bits.signed(57, 28);
            long latRaw = bits.signed(85, 27);
            long cogRaw = bits.unsigned(112, 12);
            long headingRaw = bits.unsigned(124, 9);

            double lon = coordinate(lonRaw, 180.0);
            double lat = coordinate(latRaw, 90.0);
            double sog = sogRaw == 1023 ? Double.NaN : sogRaw / 10.0;
            double cog = cogRaw >= 3600 ? Double.NaN : cogRaw / 10.0;
            Integer heading = headingRaw == 511 ? null : (int) headingRaw;

            return new AisTarget(type, mmsi, lat, lon, sog, cog, heading, null, now);
        }

        return null;
    }

    private static double coordinate(long raw, double maxAbs) {
        double value = raw / 600000.0;
        return Math.abs(value) > maxAbs ? Double.NaN : value;
    }

    public static boolean checksumValidIfPresent(String sentence) {
        int star = sentence.indexOf('*');
        if (star < 0) return true;
        if (star + 2 >= sentence.length()) return false;

        int start = (sentence.startsWith("!") || sentence.startsWith("$")) ? 1 : 0;
        int checksum = 0;
        for (int i = start; i < star; i++) checksum ^= sentence.charAt(i);

        try {
            int expected = Integer.parseInt(sentence.substring(star + 1, star + 3), 16);
            return checksum == expected;
        } catch (Exception e) {
            return false;
        }
    }

    private static Integer integer(String raw) {
        try { return Integer.parseInt(raw.trim()); }
        catch (Exception e) { return null; }
    }

    static final class BitBuffer {
        private final boolean[] bits;

        private BitBuffer(boolean[] bits) {
            this.bits = bits;
        }

        static BitBuffer fromPayload(String payload, int fillBits) {
            int total = payload.length() * 6 - fillBits;
            if (total <= 0) throw new IllegalArgumentException("empty AIS payload");
            boolean[] out = new boolean[total];
            int index = 0;
            for (int i = 0; i < payload.length(); i++) {
                int v = payload.charAt(i) - 48;
                if (v > 40) v -= 8;
                if (v < 0 || v > 63) throw new IllegalArgumentException("invalid AIS six-bit char");
                for (int b = 5; b >= 0 && index < total; b--) {
                    out[index++] = ((v >> b) & 1) != 0;
                }
            }
            return new BitBuffer(out);
        }

        int length() {
            return bits.length;
        }

        long unsigned(int start, int length) {
            if (length <= 0 || length > 63 || start < 0 || start + length > bits.length) {
                throw new IllegalArgumentException("AIS bit range");
            }
            long value = 0L;
            for (int i = 0; i < length; i++) {
                value = (value << 1) | (bits[start + i] ? 1L : 0L);
            }
            return value;
        }

        long signed(int start, int length) {
            long value = unsigned(start, length);
            long sign = 1L << (length - 1);
            if ((value & sign) != 0) value -= (1L << length);
            return value;
        }
    }
}

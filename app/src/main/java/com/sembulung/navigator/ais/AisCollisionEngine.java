package com.sembulung.navigator.ais;

public final class AisCollisionEngine {
    private AisCollisionEngine() {}

    public enum Risk {
        DANGER,
        WARNING,
        MONITOR,
        SAFE,
        UNKNOWN
    }

    public static final class Assessment {
        public final double rangeNm;
        public final double bearingDeg;
        public final double cpaNm;
        public final double tcpaMinutes;
        public final Risk risk;

        public Assessment(double rangeNm, double bearingDeg, double cpaNm, double tcpaMinutes, Risk risk) {
            this.rangeNm = rangeNm;
            this.bearingDeg = bearingDeg;
            this.cpaNm = cpaNm;
            this.tcpaMinutes = tcpaMinutes;
            this.risk = risk;
        }
    }

    /**
     * Relative-motion CPA/TCPA approximation in a local tangent plane.
     * Distances are nautical miles, speeds knots, TCPA minutes.
     */
    public static Assessment assess(
            double ownLat,
            double ownLon,
            double ownSpeedKnots,
            double ownCourseDeg,
            AisTarget target) {

        if (target == null || !target.hasValidPosition()) {
            return new Assessment(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Risk.UNKNOWN);
        }

        double meanLatRad = Math.toRadians((ownLat + target.latitude) * 0.5);
        double north = (target.latitude - ownLat) * 60.0;
        double east = (target.longitude - ownLon) * 60.0 * Math.cos(meanLatRad);
        double range = Math.hypot(east, north);
        double bearing = normalize360(Math.toDegrees(Math.atan2(east, north)));

        if (Double.isNaN(ownSpeedKnots) || Double.isNaN(ownCourseDeg) || !target.hasMotionVector()) {
            return new Assessment(range, bearing, Double.NaN, Double.NaN, Risk.UNKNOWN);
        }

        double[] ownV = velocity(ownSpeedKnots, ownCourseDeg);
        double[] targetV = velocity(target.speedKnots, target.courseDeg);
        double relEast = targetV[0] - ownV[0];
        double relNorth = targetV[1] - ownV[1];
        double relSpeedSq = relEast * relEast + relNorth * relNorth;

        if (relSpeedSq < 1e-8) {
            Risk risk = range <= 0.5 ? Risk.MONITOR : Risk.SAFE;
            return new Assessment(range, bearing, range, Double.POSITIVE_INFINITY, risk);
        }

        double tcpaHours = -(east * relEast + north * relNorth) / relSpeedSq;
        double tcpaMinutes = tcpaHours * 60.0;

        double cpa;
        if (tcpaHours < 0.0) {
            cpa = range;
        } else {
            cpa = Math.hypot(east + relEast * tcpaHours, north + relNorth * tcpaHours);
        }

        Risk risk = classify(range, cpa, tcpaMinutes);
        return new Assessment(range, bearing, cpa, tcpaMinutes, risk);
    }

    static Risk classify(double rangeNm, double cpaNm, double tcpaMinutes) {
        if (Double.isNaN(cpaNm) || Double.isNaN(tcpaMinutes)) return Risk.UNKNOWN;
        if (tcpaMinutes >= 0.0 && tcpaMinutes <= 15.0 && cpaNm <= 0.5) return Risk.DANGER;
        if (tcpaMinutes >= 0.0 && tcpaMinutes <= 30.0 && cpaNm <= 1.0) return Risk.WARNING;
        if (tcpaMinutes >= 0.0 && tcpaMinutes <= 60.0 && (cpaNm <= 2.0 || rangeNm <= 2.0)) return Risk.MONITOR;
        return Risk.SAFE;
    }

    private static double[] velocity(double speedKnots, double courseDeg) {
        double r = Math.toRadians(courseDeg);
        return new double[]{
                speedKnots * Math.sin(r),  // east
                speedKnots * Math.cos(r)   // north
        };
    }

    private static double normalize360(double angle) {
        double r = angle % 360.0;
        if (r < 0) r += 360.0;
        return r;
    }
}

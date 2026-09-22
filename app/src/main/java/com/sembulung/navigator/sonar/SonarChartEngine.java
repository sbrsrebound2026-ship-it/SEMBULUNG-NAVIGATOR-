package com.sembulung.navigator.sonar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SonarChartEngine {
    private static final double METERS_PER_DEG_LAT = 110540.0;

    public SonarChartSnapshot build(List<DepthSample> input,
                                    double cellMeters,
                                    double contourIntervalMeters) {
        if (cellMeters < 2.0) cellMeters = 2.0;
        if (contourIntervalMeters <= 0.0) contourIntervalMeters = 1.0;
        if (input == null || input.isEmpty()) {
            return SonarChartSnapshot.empty(cellMeters, contourIntervalMeters);
        }

        List<DepthSample> samples = new ArrayList<>();
        double refLat = 0.0;
        double refLon = 0.0;
        int good = 0;

        for (DepthSample s : input) {
            if (s != null && s.isUsable()) {
                samples.add(s);
                refLat += s.latitude;
                refLon += s.longitude;
                if (s.quality == DepthSample.Quality.GOOD) good++;
            }
        }

        if (samples.isEmpty()) {
            return SonarChartSnapshot.empty(cellMeters, contourIntervalMeters);
        }

        refLat /= samples.size();
        refLon /= samples.size();
        double metersPerDegLon = 111320.0 * Math.max(0.05, Math.cos(Math.toRadians(refLat)));

        Map<Key, Accum> acc = new HashMap<>();
        double minDepth = Double.POSITIVE_INFINITY;
        double maxDepth = Double.NEGATIVE_INFINITY;

        for (DepthSample s : samples) {
            double x = (s.longitude - refLon) * metersPerDegLon;
            double y = (s.latitude - refLat) * METERS_PER_DEG_LAT;
            int ix = floorIndex(x, cellMeters);
            int iy = floorIndex(y, cellMeters);
            Key k = new Key(ix, iy);
            Accum a = acc.get(k);
            if (a == null) {
                a = new Accum();
                acc.put(k, a);
            }
            double weight = s.quality == DepthSample.Quality.GOOD ? 1.0 : 0.35;
            a.sum += s.depthMeters * weight;
            a.weight += weight;
            a.count++;
            minDepth = Math.min(minDepth, s.depthMeters);
            maxDepth = Math.max(maxDepth, s.depthMeters);
        }

        Map<Key, Node> nodes = new HashMap<>();
        List<SonarChartSnapshot.Cell> cells = new ArrayList<>();
        for (Map.Entry<Key, Accum> e : acc.entrySet()) {
            Key k = e.getKey();
            Accum a = e.getValue();
            double depth = a.sum / Math.max(0.0001, a.weight);

            double minX = k.x * cellMeters;
            double minY = k.y * cellMeters;
            double maxX = minX + cellMeters;
            double maxY = minY + cellMeters;
            double cx = minX + cellMeters / 2.0;
            double cy = minY + cellMeters / 2.0;

            double minLat = refLat + minY / METERS_PER_DEG_LAT;
            double maxLat = refLat + maxY / METERS_PER_DEG_LAT;
            double minLon = refLon + minX / metersPerDegLon;
            double maxLon = refLon + maxX / metersPerDegLon;
            double centerLat = refLat + cy / METERS_PER_DEG_LAT;
            double centerLon = refLon + cx / metersPerDegLon;

            cells.add(new SonarChartSnapshot.Cell(
                    minLat, minLon, maxLat, maxLon,
                    centerLat, centerLon, depth, a.count));
            nodes.put(k, new Node(centerLat, centerLon, depth));
        }

        List<SonarChartSnapshot.ContourSegment> contours =
                createContours(nodes, contourIntervalMeters);

        return new SonarChartSnapshot(
                cells,
                contours,
                samples.size(),
                good,
                minDepth,
                maxDepth,
                cells.size() * cellMeters * cellMeters,
                cellMeters,
                contourIntervalMeters);
    }

    private List<SonarChartSnapshot.ContourSegment> createContours(
            Map<Key, Node> nodes, double interval) {
        List<SonarChartSnapshot.ContourSegment> out = new ArrayList<>();

        for (Map.Entry<Key, Node> entry : nodes.entrySet()) {
            Key k = entry.getKey();
            Node p00 = nodes.get(k);
            Node p10 = nodes.get(new Key(k.x + 1, k.y));
            Node p11 = nodes.get(new Key(k.x + 1, k.y + 1));
            Node p01 = nodes.get(new Key(k.x, k.y + 1));

            if (p00 == null || p10 == null || p11 == null || p01 == null) continue;

            double localMin = Math.min(Math.min(p00.depth, p10.depth), Math.min(p11.depth, p01.depth));
            double localMax = Math.max(Math.max(p00.depth, p10.depth), Math.max(p11.depth, p01.depth));
            double first = Math.ceil(localMin / interval) * interval;

            for (double level = first; level <= localMax + 1e-9; level += interval) {
                List<Point> hits = new ArrayList<>(4);
                addUnique(hits, interpolate(p00, p10, level));
                addUnique(hits, interpolate(p10, p11, level));
                addUnique(hits, interpolate(p11, p01, level));
                addUnique(hits, interpolate(p01, p00, level));

                if (hits.size() == 2) {
                    addSegment(out, level, hits.get(0), hits.get(1));
                } else if (hits.size() == 4) {
                    addSegment(out, level, hits.get(0), hits.get(1));
                    addSegment(out, level, hits.get(2), hits.get(3));
                }
            }
        }
        return out;
    }

    private Point interpolate(Node a, Node b, double level) {
        double lo = Math.min(a.depth, b.depth);
        double hi = Math.max(a.depth, b.depth);
        if (level < lo - 1e-9 || level > hi + 1e-9) return null;
        if (Math.abs(a.depth - b.depth) < 1e-9) return null;
        double t = (level - a.depth) / (b.depth - a.depth);
        if (t < -1e-9 || t > 1.0 + 1e-9) return null;
        t = Math.max(0.0, Math.min(1.0, t));
        return new Point(
                a.lat + (b.lat - a.lat) * t,
                a.lon + (b.lon - a.lon) * t);
    }

    private void addUnique(List<Point> points, Point p) {
        if (p == null) return;
        for (Point q : points) {
            if (Math.abs(q.lat - p.lat) < 1e-10 && Math.abs(q.lon - p.lon) < 1e-10) return;
        }
        points.add(p);
    }

    private void addSegment(List<SonarChartSnapshot.ContourSegment> out,
                            double level, Point a, Point b) {
        if (a == null || b == null) return;
        if (Math.abs(a.lat - b.lat) < 1e-12 && Math.abs(a.lon - b.lon) < 1e-12) return;
        out.add(new SonarChartSnapshot.ContourSegment(
                level, a.lat, a.lon, b.lat, b.lon));
    }

    private int floorIndex(double meters, double cellMeters) {
        return (int) Math.floor(meters / cellMeters);
    }

    private static final class Accum {
        double sum;
        double weight;
        int count;
    }

    private static final class Node {
        final double lat;
        final double lon;
        final double depth;
        Node(double lat, double lon, double depth) {
            this.lat = lat;
            this.lon = lon;
            this.depth = depth;
        }
    }

    private static final class Point {
        final double lat;
        final double lon;
        Point(double lat, double lon) {
            this.lat = lat;
            this.lon = lon;
        }
    }

    private static final class Key {
        final int x;
        final int y;
        Key(int x, int y) {
            this.x = x;
            this.y = y;
        }
        @Override public boolean equals(Object o) {
            if (!(o instanceof Key)) return false;
            Key k = (Key)o;
            return x == k.x && y == k.y;
        }
        @Override public int hashCode() {
            return x * 73856093 ^ y * 19349663;
        }
    }
}

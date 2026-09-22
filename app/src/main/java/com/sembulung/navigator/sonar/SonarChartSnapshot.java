package com.sembulung.navigator.sonar;

import java.util.Collections;
import java.util.List;

public final class SonarChartSnapshot {
    public final List<Cell> cells;
    public final List<ContourSegment> contours;
    public final int sampleCount;
    public final int goodSampleCount;
    public final double minDepth;
    public final double maxDepth;
    public final double surveyedAreaSquareMeters;
    public final double cellMeters;
    public final double contourIntervalMeters;

    public SonarChartSnapshot(List<Cell> cells, List<ContourSegment> contours,
                              int sampleCount, int goodSampleCount,
                              double minDepth, double maxDepth,
                              double surveyedAreaSquareMeters,
                              double cellMeters, double contourIntervalMeters) {
        this.cells = Collections.unmodifiableList(cells);
        this.contours = Collections.unmodifiableList(contours);
        this.sampleCount = sampleCount;
        this.goodSampleCount = goodSampleCount;
        this.minDepth = minDepth;
        this.maxDepth = maxDepth;
        this.surveyedAreaSquareMeters = surveyedAreaSquareMeters;
        this.cellMeters = cellMeters;
        this.contourIntervalMeters = contourIntervalMeters;
    }

    public static SonarChartSnapshot empty(double cellMeters, double contourIntervalMeters) {
        return new SonarChartSnapshot(Collections.emptyList(), Collections.emptyList(),
                0, 0, Double.NaN, Double.NaN, 0.0, cellMeters, contourIntervalMeters);
    }

    public static final class Cell {
        public final double minLat;
        public final double minLon;
        public final double maxLat;
        public final double maxLon;
        public final double centerLat;
        public final double centerLon;
        public final double depthMeters;
        public final int sampleCount;

        public Cell(double minLat, double minLon, double maxLat, double maxLon,
                    double centerLat, double centerLon, double depthMeters, int sampleCount) {
            this.minLat = minLat;
            this.minLon = minLon;
            this.maxLat = maxLat;
            this.maxLon = maxLon;
            this.centerLat = centerLat;
            this.centerLon = centerLon;
            this.depthMeters = depthMeters;
            this.sampleCount = sampleCount;
        }
    }

    public static final class ContourSegment {
        public final double depthMeters;
        public final double lat1;
        public final double lon1;
        public final double lat2;
        public final double lon2;

        public ContourSegment(double depthMeters, double lat1, double lon1,
                              double lat2, double lon2) {
            this.depthMeters = depthMeters;
            this.lat1 = lat1;
            this.lon1 = lon1;
            this.lat2 = lat2;
            this.lon2 = lon2;
        }
    }
}

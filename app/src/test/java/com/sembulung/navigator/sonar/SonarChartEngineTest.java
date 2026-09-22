package com.sembulung.navigator.sonar;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class SonarChartEngineTest {
    @Test public void buildsGridAndStatistics() {
        List<DepthSample> samples = squareSamples();
        SonarChartSnapshot s = new SonarChartEngine().build(samples, 30.0, 5.0);

        assertEquals(4, s.sampleCount);
        assertEquals(4, s.goodSampleCount);
        assertTrue(s.cells.size() >= 4);
        assertEquals(5.0, s.minDepth, 0.001);
        assertEquals(20.0, s.maxDepth, 0.001);
        assertTrue(s.surveyedAreaSquareMeters > 0.0);
    }

    @Test public void createsContourVectorSegments() {
        List<DepthSample> samples = squareSamples();
        SonarChartSnapshot s = new SonarChartEngine().build(samples, 30.0, 5.0);

        assertFalse("Contour vector should be generated from adjacent depth cells",
                s.contours.isEmpty());
        boolean hasTen = false;
        for (SonarChartSnapshot.ContourSegment c : s.contours) {
            if (Math.abs(c.depthMeters - 10.0) < 0.001) hasTen = true;
        }
        assertTrue("Expected a 10 m contour", hasTen);
    }

    @Test public void rejectedSamplesDoNotEnterChart() {
        List<DepthSample> samples = new ArrayList<>();
        samples.add(new DepthSample(-8.0, 114.0, 12.0, 1L, null, null,
                DepthSample.Quality.GOOD, "TEST"));
        samples.add(new DepthSample(-8.0, 114.0, 9999.0, 2L, null, null,
                DepthSample.Quality.REJECTED, "TEST"));

        SonarChartSnapshot s = new SonarChartEngine().build(samples, 20.0, 2.0);
        assertEquals(1, s.sampleCount);
        assertEquals(12.0, s.minDepth, 0.001);
        assertEquals(12.0, s.maxDepth, 0.001);
    }

    private List<DepthSample> squareSamples() {
        double lat = -8.5000;
        double lon = 114.4000;
        double d = 0.00010;
        List<DepthSample> a = new ArrayList<>();
        a.add(new DepthSample(lat-d, lon-d, 5.0, 1L, null, null, DepthSample.Quality.GOOD, "TEST"));
        a.add(new DepthSample(lat-d, lon+d, 15.0, 2L, null, null, DepthSample.Quality.GOOD, "TEST"));
        a.add(new DepthSample(lat+d, lon-d, 10.0, 3L, null, null, DepthSample.Quality.GOOD, "TEST"));
        a.add(new DepthSample(lat+d, lon+d, 20.0, 4L, null, null, DepthSample.Quality.GOOD, "TEST"));
        return a;
    }
}

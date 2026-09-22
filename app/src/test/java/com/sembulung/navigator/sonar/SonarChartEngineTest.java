package com.sembulung.navigator.sonar;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class SonarChartEngineTest {
    @Test public void buildsBathymetryCellsAndStats(){
        List<DepthSample> s=gridSamples();
        SonarChartEngine.Chart c=SonarChartEngine.build(s,20,5);
        assertFalse(c.cells.isEmpty());
        assertEquals(s.size(),c.stats.acceptedSoundings);
        assertTrue(c.stats.minDepth>=5.0);
        assertTrue(c.stats.maxDepth>c.stats.minDepth);
        assertTrue(c.stats.coverageSquareMeters>0);
    }

    @Test public void producesVectorContoursForGradient(){
        SonarChartEngine.Chart c=SonarChartEngine.build(gridSamples(),15,2);
        assertFalse("Expected contour vector segments",c.contours.isEmpty());
        for(SonarChartEngine.ContourSegment seg:c.contours){
            assertTrue(Double.isFinite(seg.lat1));
            assertTrue(Double.isFinite(seg.lon1));
            assertTrue(seg.level>0);
        }
    }

    @Test public void rejectedSoundingIsExcluded(){
        List<DepthSample> s=gridSamples();
        s.add(new DepthSample(-8.0,114.0,999,1,null,null,DepthSample.Quality.REJECTED,"test"));
        SonarChartEngine.Chart c=SonarChartEngine.build(s,20,5);
        assertEquals(s.size()-1,c.stats.acceptedSoundings);
        assertTrue(c.stats.maxDepth<999);
    }

    private static List<DepthSample> gridSamples(){
        ArrayList<DepthSample> out=new ArrayList<>();
        double lat=-8.5000,lon=114.3000;
        long t=1000;
        for(int y=0;y<6;y++) for(int x=0;x<6;x++){
            double depth=5.0+x*2.0+y*1.5;
            out.add(new DepthSample(lat+y*0.00018,lon+x*0.00018,depth,t++,
                    6.0,90.0,DepthSample.Quality.GOOD,"test"));
        }
        return out;
    }
}

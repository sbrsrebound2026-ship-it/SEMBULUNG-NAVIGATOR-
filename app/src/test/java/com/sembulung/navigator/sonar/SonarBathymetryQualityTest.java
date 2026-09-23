package com.sembulung.navigator.sonar;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class SonarBathymetryQualityTest {
    @Test public void chartCarriesConfidenceAndRelief(){
        List<DepthSample> s=new ArrayList<>();
        double lat=-8.45,lon=114.30;
        for(int y=0;y<8;y++)for(int x=0;x<8;x++){
            s.add(new DepthSample(lat+y*.00012,lon+x*.00012,5+x+y*.6,1000+x+y,
                    5.0,90.0,DepthSample.Quality.GOOD,"test"));
        }
        SonarChartEngine.Chart c=SonarChartEngine.build(s,10,1);
        assertFalse(c.cells.isEmpty());
        assertTrue(c.stats.meanConfidence>0);
        assertEquals(1.0,c.stats.contourIntervalMeters,1e-9);
        boolean hasConfidence=false;
        for(SonarChartEngine.Cell cell:c.cells){
            if(cell.confidence>0){hasConfidence=true;break;}
        }
        assertTrue(hasConfidence);
    }

    @Test public void questionableSamplesStillProduceLowerConfidenceCoverage(){
        List<DepthSample> good=new ArrayList<>();
        List<DepthSample> q=new ArrayList<>();
        for(int i=0;i<12;i++){
            double lat=-8.4+i*.0001;
            good.add(new DepthSample(lat,114.3,10+i*.2,1000+i,null,null,DepthSample.Quality.GOOD,"g"));
            q.add(new DepthSample(lat,114.3,10+i*.2,1000+i,null,null,DepthSample.Quality.QUESTIONABLE,"q"));
        }
        SonarChartEngine.Chart cg=SonarChartEngine.build(good,10,2);
        SonarChartEngine.Chart cq=SonarChartEngine.build(q,10,2);
        assertTrue(cg.stats.meanConfidence>=cq.stats.meanConfidence);
    }
}

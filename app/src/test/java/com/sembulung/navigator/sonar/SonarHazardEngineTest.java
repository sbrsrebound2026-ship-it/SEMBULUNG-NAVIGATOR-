package com.sembulung.navigator.sonar;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import static org.junit.Assert.*;

public class SonarHazardEngineTest {
    @Test public void detectsShallowCellAhead(){
        SonarChartEngine.Cell ahead=new SonarChartEngine.Cell(
                0.0017,-0.0001,0.0019,0.0001,2.0);
        SonarChartEngine.Cell safe=new SonarChartEngine.Cell(
                0.0037,-0.0001,0.0039,0.0001,12.0);
        SonarChartEngine.Chart chart=new SonarChartEngine.Chart(
                Arrays.asList(ahead,safe),new ArrayList<>(),new ArrayList<>(),
                new SonarChartEngine.Stats(2,2,12,7,800,20));
        SonarHazardEngine.Assessment a=SonarHazardEngine.assessAhead(
                chart,0.0,0.0,0.0,8.0,5.0,5.0,60.0);
        assertEquals(SonarHazardEngine.Risk.DANGER,a.risk);
        assertEquals(2.0,a.minimumDepthMeters,0.01);
        assertTrue(a.distanceAheadMeters>100);
    }

    @Test public void ignoresShallowCellBehind(){
        SonarChartEngine.Cell behind=new SonarChartEngine.Cell(
                -0.002,-0.0001,-0.0018,0.0001,1.5);
        SonarChartEngine.Chart chart=new SonarChartEngine.Chart(
                Arrays.asList(behind),new ArrayList<>(),new ArrayList<>(),
                new SonarChartEngine.Stats(1,1.5,1.5,1.5,400,20));
        SonarHazardEngine.Assessment a=SonarHazardEngine.assessAhead(
                chart,0.0,0.0,0.0,8.0,5.0,5.0,60.0);
        assertEquals(SonarHazardEngine.Risk.UNKNOWN,a.risk);
    }
}

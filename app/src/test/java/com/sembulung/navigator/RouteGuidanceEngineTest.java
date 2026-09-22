package com.sembulung.navigator;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class RouteGuidanceEngineTest {
    @Test public void computesDistanceBearingEta(){
        List<WaypointStore.Waypoint> w=Arrays.asList(
                new WaypointStore.Waypoint("A",0.0,0.0),
                new WaypointStore.Waypoint("B",0.1,0.0));
        RouteGuidanceEngine.Guidance g=RouteGuidanceEngine.assess(0.05,0.0,10.0,w,1,0.03);
        assertNotNull(g);
        assertEquals(3.0,g.distanceNm,0.05);
        assertEquals(0.0,g.bearingDeg,0.5);
        assertEquals(18.0,g.etaMinutes,0.5);
        assertEquals(0.0,g.xteNm,0.01);
        assertFalse(g.arrived);
    }

    @Test public void computesSignedCrossTrack(){
        List<WaypointStore.Waypoint> w=Arrays.asList(
                new WaypointStore.Waypoint("A",0.0,0.0),
                new WaypointStore.Waypoint("B",0.1,0.0));
        RouteGuidanceEngine.Guidance g=RouteGuidanceEngine.assess(0.05,0.01,5.0,w,1,0.03);
        assertTrue(Math.abs(g.xteNm)>0.5);
    }

    @Test public void advancesAndEndsRoute(){
        assertEquals(2,RouteGuidanceEngine.nextIndex(1,4,true,true));
        assertEquals(-1,RouteGuidanceEngine.nextIndex(3,4,true,true));
        assertEquals(1,RouteGuidanceEngine.nextIndex(1,4,true,false));
    }
}

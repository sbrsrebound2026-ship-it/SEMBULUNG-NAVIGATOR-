package com.sembulung.navigator.sonar;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class SonarReplayEngineTest {
    @Test public void replaysInOrderAndResets(){
        SonarReplayEngine r=new SonarReplayEngine(Arrays.asList(
                new DepthSample(0,0,1,1,null,null,DepthSample.Quality.GOOD,"a"),
                new DepthSample(0,0,2,2,null,null,DepthSample.Quality.GOOD,"b")));
        assertEquals(1.0,r.next().depthMeters,0.0);
        assertEquals(2.0,r.next().depthMeters,0.0);
        assertFalse(r.hasNext());
        r.reset();
        assertTrue(r.hasNext());
        assertEquals(0,r.position());
    }
}

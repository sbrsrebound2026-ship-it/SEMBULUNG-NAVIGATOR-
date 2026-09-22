package com.sembulung.navigator;

import org.junit.Test;
import static org.junit.Assert.*;

public class NmeaSentenceParserTest {
    @Test public void parsesRmcPositionSpeedAndCourse(){
        NmeaSentenceParser.State s=new NmeaSentenceParser.State();
        long t=123456L;
        assertTrue(NmeaSentenceParser.apply(
                "$GPRMC,123519,A,0830.000,S,11420.000,E,7.5,84.4,230394,,",s,t));
        assertEquals(-8.5,s.lat,1e-6);
        assertEquals(114.333333,s.lon,1e-6);
        assertEquals(7.5,s.speed,1e-9);
        assertEquals(84.4,s.heading,1e-9);
        assertEquals(t,s.positionTime);
        assertEquals(t,s.headingTime);
    }

    @Test public void parsesDepth(){
        NmeaSentenceParser.State s=new NmeaSentenceParser.State();
        assertTrue(NmeaSentenceParser.apply("$SDDPT,18.7,0.0,",s,77L));
        assertEquals(18.7,s.depth,1e-9);
        assertEquals(77L,s.depthTime);
    }

    @Test public void rejectsBadChecksum(){
        NmeaSentenceParser.State s=new NmeaSentenceParser.State();
        assertFalse(NmeaSentenceParser.apply("$GPRMC,1,A,0830.000,S,11420.000,E,1,2,3*00",s,1L));
    }

    @Test public void acceptsKnownGoodChecksum(){
        NmeaSentenceParser.State s=new NmeaSentenceParser.State();
        assertTrue(NmeaSentenceParser.apply("$GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A",s,1L));
        assertEquals(48.1173,s.lat,0.0001);
        assertEquals(11.5166667,s.lon,0.0001);
    }
}

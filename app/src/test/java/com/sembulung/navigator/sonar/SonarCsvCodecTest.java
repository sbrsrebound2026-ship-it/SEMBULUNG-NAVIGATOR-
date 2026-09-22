package com.sembulung.navigator.sonar;

import org.junit.Test;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

public class SonarCsvCodecTest {
    @Test public void roundTripsCsv() throws Exception{
        List<DepthSample> src=Arrays.asList(
                new DepthSample(-8.1,114.2,12.345,123L,6.5,88.0,DepthSample.Quality.GOOD,"udp,sonar"),
                new DepthSample(-8.2,114.3,7.0,124L,null,null,DepthSample.Quality.QUESTIONABLE,"NMEA"));
        StringWriter w=new StringWriter();
        SonarCsvCodec.writeAll(w,src);
        List<DepthSample> got=SonarCsvCodec.readAll(new StringReader(w.toString()),0);
        assertEquals(2,got.size());
        assertEquals(12.345,got.get(0).depthMeters,0.001);
        assertEquals("udp;sonar",got.get(0).source);
        assertEquals(DepthSample.Quality.QUESTIONABLE,got.get(1).quality);
        assertNull(got.get(1).speedKnots);
    }

    @Test public void ignoresMalformedRows() throws Exception{
        String raw=SonarCsvCodec.HEADER+"\nBAD\n123,-8,114,10,,,GOOD,test\n";
        List<DepthSample> got=SonarCsvCodec.readAll(new StringReader(raw),0);
        assertEquals(1,got.size());
    }
}

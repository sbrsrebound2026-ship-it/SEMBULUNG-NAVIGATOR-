package com.sembulung.navigator;

import org.junit.Test;
import static org.junit.Assert.*;

public class CoordinateParserTest {
    @Test public void parsesDecimalPair(){
        CoordinateParser.Point p=CoordinateParser.parsePair("-8.123456, 114.123456");
        assertNotNull(p);
        assertEquals(-8.123456,p.lat,1e-8);
        assertEquals(114.123456,p.lon,1e-8);
    }

    @Test public void parsesDmsPair(){
        CoordinateParser.Point p=CoordinateParser.parsePair("8°07'24.4\"S 114°07'24.4\"E");
        assertNotNull(p);
        assertTrue(p.lat<0);
        assertTrue(p.lon>0);
        assertEquals(-8.123444,p.lat,0.00001);
        assertEquals(114.123444,p.lon,0.00001);
    }

    @Test public void rejectsOutOfRange(){
        assertNull(CoordinateParser.parsePair("-98.0, 214.0"));
    }
}

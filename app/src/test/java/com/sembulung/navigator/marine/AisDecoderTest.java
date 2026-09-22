package com.sembulung.navigator.marine;

import org.junit.Test;
import static org.junit.Assert.*;

public class AisDecoderTest {
    @Test public void rejectsNonAis() {
        assertNull(AisDecoder.decodeSingleFragment("$GPRMC,123519,A,0830.000,S,11420.000,E,7.5,84.4,230394,,"));
    }

    @Test public void rejectsMultipartForNow() {
        assertNull(AisDecoder.decodeSingleFragment("!AIVDM,2,1,1,A,55NBsv02>tJ@4P@E>20@E=B1HE=<Dh0000000016,0*00"));
    }

    @Test public void decodesKnownSingleFragmentPosition() {
        AisDecoder.PositionReport r = AisDecoder.decodeSingleFragment("!AIVDM,1,1,,A,15Muq?P0000G?tRMD5MTDwwT0<0u,0*5C");
        assertNotNull(r);
        assertTrue(r.messageType == 1 || r.messageType == 2 || r.messageType == 3);
        assertTrue(r.mmsi > 0);
        assertTrue(r.latitude >= -90 && r.latitude <= 90);
        assertTrue(r.longitude >= -180 && r.longitude <= 180);
    }
}

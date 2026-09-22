package com.sembulung.navigator.ais;

import org.junit.Test;
import static org.junit.Assert.*;

public class AisParserTest {
    @Test public void decodesSyntheticClassAPosition() {
        String payload = classAPayload(123456789L,-8.500000,114.333333,12.3,84.5,85);
        AisTarget target = AisParser.parse("!AIVDM,1,1,,A," + payload + ",0");

        assertNotNull(target);
        assertEquals(1,target.messageType);
        assertEquals(123456789L,target.mmsi);
        assertEquals(-8.5,target.latitude,0.00001);
        assertEquals(114.333333,target.longitude,0.00001);
        assertEquals(12.3,target.speedKnots,0.01);
        assertEquals(84.5,target.courseDeg,0.01);
        assertEquals(Integer.valueOf(85),target.headingDeg);
    }

    @Test public void rejectsWrongChecksum() {
        String payload = classAPayload(123456789L,-8.5,114.3,5.0,90.0,90);
        assertNull(AisParser.parse("!AIVDM,1,1,,A," + payload + ",0*00"));
    }

    private static String classAPayload(long mmsi,double lat,double lon,double sog,double cog,int heading) {
        boolean[] bits = new boolean[168];
        putUnsigned(bits,0,6,1);
        putUnsigned(bits,6,2,0);
        putUnsigned(bits,8,30,mmsi);
        putUnsigned(bits,38,4,0);
        putSigned(bits,42,8,0);
        putUnsigned(bits,50,10,Math.round(sog*10.0));
        putUnsigned(bits,60,1,1);
        putSigned(bits,61,28,Math.round(lon*600000.0));
        putSigned(bits,89,27,Math.round(lat*600000.0));
        putUnsigned(bits,116,12,Math.round(cog*10.0));
        putUnsigned(bits,128,9,heading);
        putUnsigned(bits,137,6,30);
        return encode(bits);
    }

    private static void putUnsigned(boolean[] bits,int start,int length,long value) {
        for(int i=0;i<length;i++) {
            int shift=length-1-i;
            bits[start+i]=((value>>shift)&1L)!=0;
        }
    }

    private static void putSigned(boolean[] bits,int start,int length,long value) {
        long encoded=value;
        if(value<0) encoded=(1L<<length)+value;
        putUnsigned(bits,start,length,encoded);
    }

    private static String encode(boolean[] bits) {
        StringBuilder out=new StringBuilder();
        for(int i=0;i<bits.length;i+=6) {
            int v=0;
            for(int b=0;b<6;b++) {
                v=(v<<1) | ((i+b<bits.length && bits[i+b]) ? 1 : 0);
            }
            int c=v+48;
            if(c>87)c+=8;
            out.append((char)c);
        }
        return out.toString();
    }
}

package com.sembulung.navigator.sonar;

import org.junit.Test;
import static org.junit.Assert.*;

public class BathymetryStyleTest {
    @Test public void ultraGetsDenserWhenZoomedIn(){
        double far=BathymetryStyle.contourInterval(5.0,BathymetryStyle.ULTRA,11);
        double near=BathymetryStyle.contourInterval(5.0,BathymetryStyle.ULTRA,17);
        assertTrue(near<far);
        assertEquals(.5,near,1e-9);
    }

    @Test public void highDensityUsesSmallerGrid(){
        assertTrue(BathymetryStyle.cellMeters(BathymetryStyle.HIGH,15)
                <BathymetryStyle.cellMeters(BathymetryStyle.NORMAL,15));
    }

    @Test public void majorContoursAreWiderSpaced(){
        assertEquals(5.0,BathymetryStyle.majorInterval(1.0),1e-9);
        assertEquals(10.0,BathymetryStyle.majorInterval(2.0),1e-9);
        assertEquals(20.0,BathymetryStyle.majorInterval(5.0),1e-9);
    }
}

package com.sembulung.navigator.ais;

import org.junit.Test;
import static org.junit.Assert.*;

public class AisCollisionEngineTest {
    @Test public void detectsHeadOnDanger() {
        // ~2 NM north, both vessels closing at 20 kn relative => TCPA ~6 min, CPA ~0.
        AisTarget target = new AisTarget(
                1,111000111L,
                0.033333333,0.0,
                10.0,180.0,
                180,0,System.currentTimeMillis());

        AisCollisionEngine.Assessment a = AisCollisionEngine.assess(
                0.0,0.0,
                10.0,0.0,
                target);

        assertEquals(2.0,a.rangeNm,0.02);
        assertEquals(0.0,a.cpaNm,0.02);
        assertEquals(6.0,a.tcpaMinutes,0.1);
        assertEquals(AisCollisionEngine.Risk.DANGER,a.risk);
    }

    @Test public void recedingTargetIsSafe() {
        AisTarget target = new AisTarget(
                18,222000222L,
                0.016666667,0.0,
                20.0,0.0,
                0,null,System.currentTimeMillis());

        AisCollisionEngine.Assessment a = AisCollisionEngine.assess(
                0.0,0.0,
                5.0,0.0,
                target);

        assertTrue(a.tcpaMinutes < 0.0);
        assertEquals(AisCollisionEngine.Risk.SAFE,a.risk);
    }
}

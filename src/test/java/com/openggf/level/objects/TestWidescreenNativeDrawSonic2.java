package com.openggf.level.objects;

import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

/** NATIVE widescreen draw mode plays the native game; see {@link WidescreenNativeDrawEquivalence}. */
@RequiresRom(SonicGame.SONIC_2)
class TestWidescreenNativeDrawSonic2 {
    @Test
    void nativeDrawModeAtThirtyTwoByNineMatchesFourByThree() throws Exception {
        var wide = WidescreenNativeDrawEquivalence.assertEquivalent(SonicGame.SONIC_2, 0, 0, 900);
        // Placed rings (stage ring list) outside the ring window are drawn frozen too.
        org.junit.jupiter.api.Assertions.assertTrue(wide.dormantRingFrames() > 0,
                "no frame drew a dormant ring in the margins");
    }

    @Test
    void edgeDrawModeIsANegativeControl() throws Exception {
        WidescreenNativeDrawEquivalence.assertEdgeDiverges(SonicGame.SONIC_2, 0, 0, 900);
    }
}

package com.openggf.level.objects;

import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

/** NATIVE widescreen draw mode plays the native game; see {@link WidescreenNativeDrawEquivalence}. */
@RequiresRom(SonicGame.SONIC_2)
class TestWidescreenNativeDrawSonic2 {
    @Test
    void nativeDrawModeAtThirtyTwoByNineMatchesFourByThree() throws Exception {
        WidescreenNativeDrawEquivalence.assertEquivalent(SonicGame.SONIC_2, 0, 0, 900);
    }

    @Test
    void edgeDrawModeIsANegativeControl() throws Exception {
        WidescreenNativeDrawEquivalence.assertEdgeDiverges(SonicGame.SONIC_2, 0, 0, 900);
    }
}

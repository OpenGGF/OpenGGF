package com.openggf.graphics;

import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageDataLoader;
import com.openggf.level.Pattern;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Independent Nemesis bit-reader totals supplied by the native S2 mapping lane, checked through runtime loading. */
class TestNativeShadow15Rom {
    @Test @RequiresRom(SonicGame.SONIC_2)
    void allThreeOriginalShadowFamiliesUseOnlyTransparentAndNativeShadowIndices() throws Exception {
        var loader = new Sonic2SpecialStageDataLoader(TestEnvironment.currentRom());
        verify(loader.getShadowHorizPatterns(), 38, 1372, 1060);
        verify(loader.getShadowDiagPatterns(), 58, 2636, 1076);
        verify(loader.getShadowVertPatterns(), 25, 916, 684);
    }

    private static void verify(Pattern[] patterns, int count, int transparent, int shadow) {
        assertEquals(count, patterns.length);
        int[] indices = new int[16];
        for (Pattern pattern : patterns) for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++)
            indices[pattern.getPixel(x, y) & 15]++;
        assertEquals(transparent, indices[0]);
        assertEquals(shadow, indices[15]);
        for (int index = 1; index < 15; index++) assertEquals(0, indices[index], "unsupported native color " + index);
    }
}

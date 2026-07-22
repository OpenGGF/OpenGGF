package com.openggf.graphics;

import com.openggf.level.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestPatternAtlasDynamicRanges {
    private static final int MOD_BASE = 0x188000;
    private static final int WINDOW_SIZE = 0x8000;

    @Test
    void dynamicallyRegisteredAlignedRangeGovernsItsPatternIds() {
        PatternAtlas atlas = new PatternAtlas(256, 256);
        atlas.registerRange(MOD_BASE, WINDOW_SIZE, "mod:one");

        assertNotNull(atlas.cachePatternHeadless(new Pattern(), MOD_BASE));
        assertNotNull(atlas.cachePatternHeadless(new Pattern(), MOD_BASE + WINDOW_SIZE - 1));
        assertThrows(IllegalArgumentException.class,
                () -> atlas.cachePatternHeadless(new Pattern(), MOD_BASE + WINDOW_SIZE));
    }

    @Test
    void dynamicRangesRequirePositiveFourKilopatternAlignment() {
        PatternAtlas atlas = new PatternAtlas(256, 256);

        assertThrows(IllegalArgumentException.class,
                () -> atlas.registerRange(MOD_BASE + 1, WINDOW_SIZE, "misaligned-base"));
        assertThrows(IllegalArgumentException.class,
                () -> atlas.registerRange(MOD_BASE, WINDOW_SIZE - 1, "misaligned-size"));
        assertThrows(IllegalArgumentException.class,
                () -> atlas.registerRange(MOD_BASE, 0, "empty"));
        assertDoesNotThrow(() -> atlas.registerRange(MOD_BASE, WINDOW_SIZE, "aligned"));
    }

    @Test
    void dynamicRangesCannotClaimStaticRangeOwnership() {
        PatternAtlas atlas = new PatternAtlas(256, 256);

        assertThrows(IllegalArgumentException.class,
                () -> atlas.registerRange(PatternAtlasRange.MGZ_ZOOM_CUES.base(),
                        WINDOW_SIZE, "mod:collision"));
    }

}

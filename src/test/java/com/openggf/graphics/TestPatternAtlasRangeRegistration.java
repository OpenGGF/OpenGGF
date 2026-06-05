package com.openggf.graphics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestPatternAtlasRangeRegistration {

    @Test
    public void adjacentHalfOpenRangesAreValid() {
        PatternAtlas atlas = new PatternAtlas(256, 256);

        atlas.registerRange(0x38000, 0x100, "First");

        assertDoesNotThrow(() -> atlas.registerRange(0x38100, 0x100, "Adjacent"));
    }

    @Test
    public void partiallyOverlappingRangesFailFast() {
        PatternAtlas atlas = new PatternAtlas(256, 256);

        atlas.registerRange(0x38000, 0x100, "First");

        assertThrows(IllegalArgumentException.class,
            () -> atlas.registerRange(0x38080, 0x100, "Partial"));
    }

    @Test
    public void exactDuplicateRangesFailFast() {
        PatternAtlas atlas = new PatternAtlas(256, 256);

        atlas.registerRange(0x38000, 0x100, "First");

        assertThrows(IllegalArgumentException.class,
            () -> atlas.registerRange(0x38000, 0x100, "Duplicate"));
    }

    @Test
    public void enclosingRangesFailFast() {
        PatternAtlas atlas = new PatternAtlas(256, 256);

        atlas.registerRange(0x38080, 0x80, "Inner");

        assertThrows(IllegalArgumentException.class,
            () -> atlas.registerRange(0x38000, 0x200, "Outer"));
    }
}

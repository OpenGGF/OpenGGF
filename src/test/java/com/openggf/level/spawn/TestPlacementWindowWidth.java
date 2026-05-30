package com.openggf.level.spawn;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class TestPlacementWindowWidth {

    private record Point(int x, int y) implements SpawnPoint {
    }

    private static final class TestPlacement extends AbstractPlacementManager<Point> {
        TestPlacement(int extraAhead, int unloadBehind, java.util.function.IntSupplier width) {
            super(List.of(new Point(0, 0)), extraAhead, unloadBehind, width);
        }
        int windowEnd(int cameraX) { return getWindowEnd(cameraX); }
        int loadAhead() { return getLoadAhead(); }
    }

    @Test
    void nativeWindowEqualsLegacy640() {
        TestPlacement p = new TestPlacement(320, 0x80, () -> 320);
        assertEquals(640, p.loadAhead());
        assertEquals(640, p.windowEnd(0));
    }

    @Test
    void widescreenWindowScalesWithWidth() {
        TestPlacement p = new TestPlacement(320, 0x80, () -> 400);
        assertEquals(720, p.loadAhead());
        assertEquals(720, p.windowEnd(0));
    }
}

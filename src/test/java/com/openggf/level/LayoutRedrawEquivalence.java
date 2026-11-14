package com.openggf.level;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Oracle for in-place foreground redraws: after a layout writer runs and the
 * render-side ensure redraws its cells, the retained image must equal a
 * from-scratch build of the edited layout, and no full build may have run.
 *
 * <p>Lives in {@code com.openggf.level} to read the tilemap manager's package-private
 * counters and drive the render-side ensure that headless stepping does not.
 */
final class LayoutRedrawEquivalence {
    private LayoutRedrawEquivalence() { }

    /** How the edit is expected to show in the foreground image. */
    enum Foreground { CHANGED, UNCHANGED, EITHER }

    /**
     * Runs {@code writer}, flushes queued layout mutations, redraws, and checks the
     * redraw against a full rebuild. Returns the number of layout cells rewritten.
     */
    static long assertWriterRedrawsCellsInPlace(LevelManager manager, Foreground expected, Runnable writer) {
        LevelTilemapManager tilemaps = manager.getTilemapManager();
        manager.ensureForegroundTilemapData();
        byte[] image = tilemaps.getForegroundTilemapData();
        assertNotNull(image, "the foreground image must exist before the edit");
        assertArrayEquals(manager.buildForegroundImageForComparison(), image,
                "the image must match the layout before the edit");
        byte[] before = image.clone();
        int fullBuilds = tilemaps.foregroundFullBuildCount;
        int redraws = tilemaps.foregroundCellRedrawCount;
        long cells = tilemaps.foregroundRedrawnCellCount;

        writer.run();
        manager.flushQueuedLayoutMutations();
        manager.ensureForegroundTilemapData();

        assertSame(image, tilemaps.getForegroundTilemapData(), "the level image is patched in place");
        assertEquals(fullBuilds, tilemaps.foregroundFullBuildCount, "no full foreground rebuild");
        assertEquals(redraws + 1, tilemaps.foregroundCellRedrawCount, "the writer requested one redraw");
        long rewritten = tilemaps.foregroundRedrawnCellCount - cells;
        if (expected == Foreground.CHANGED) {
            assertTrue(rewritten > 0, "the edit must rewrite foreground cells");
            assertFalse(Arrays.equals(before, image), "the edit must be visible in the image");
        } else if (expected == Foreground.UNCHANGED) {
            assertEquals(0, rewritten, "a background-only edit rewrites no foreground cell");
            assertArrayEquals(before, image);
        }
        assertArrayEquals(manager.buildForegroundImageForComparison(), image,
                "the patched image differs from a full rebuild of the edited layout");
        return rewritten;
    }

    /** Invokes a private production writer by name (argument types from the values). */
    static Object invoke(Object target, String name, Object... args) {
        Class<?> type = target.getClass();
        while (type != null) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == args.length) {
                    try {
                        method.setAccessible(true);
                        return method.invoke(target, args);
                    } catch (ReflectiveOperationException e) {
                        throw new AssertionError("cannot invoke " + name, e);
                    }
                }
            }
            type = type.getSuperclass();
        }
        throw new AssertionError("no method " + name + " with " + args.length + " arguments on " + target);
    }
}

package com.openggf.graphics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestCinematicBackgroundResidency {
    @Test void privateBackgroundUploadsRestoreNativePendingDescriptorsAndPhysicalOrigins() {
        var renderer = new TilemapGpuRenderer();
        byte[] nativeData = new byte[64 * 32 * 4];
        renderer.setBackgroundTilemapDataPhysical(nativeData, 64, 32, 12, 8);
        var nativeState = renderer.captureBackgroundRenderState();
        var residency = new CinematicBackgroundResidency();
        renderer.withCinematicBackground(residency, () -> {
            byte[] directed = new byte[64 * 32 * 4];
            renderer.setBackgroundTilemapDataPhysical(directed, 64, 32, 4, 6);
            assertTrue(renderer.hasBackgroundBaseline(directed, 64, 32));
            assertFalse(renderer.hasBackgroundBaseline(nativeData, 64, 32));
        });
        assertTrue(renderer.hasBackgroundBaseline(nativeData, 64, 32));
        assertEquals(nativeState, renderer.captureBackgroundRenderState());
        assertEquals(nativeData.length, renderer.getPendingBackgroundUploadBytes());
        assertThrows(IllegalArgumentException.class, () -> renderer.withCinematicBackground(residency, () -> {
            renderer.setTilemapData(TilemapGpuRenderer.Layer.BACKGROUND, new byte[16], 2, 2);
            throw new IllegalArgumentException("failed tile");
        }));
        assertTrue(renderer.hasBackgroundBaseline(nativeData, 64, 32));
        assertEquals(nativeState, renderer.captureBackgroundRenderState());
    }

    @Test void privatePlanesAndLookupRestoreEveryNativePendingInputOnNormalAndExceptionalExit() throws Exception {
        var renderer = new TilemapGpuRenderer();
        byte[] foreground = new byte[128 * 32 * 4];
        byte[] background = new byte[64 * 64 * 4];
        byte[] lookup = {1, 2, 3, (byte) 255};
        renderer.setTilemapData(TilemapGpuRenderer.Layer.FOREGROUND, foreground, 128, 32);
        renderer.setBackgroundTilemapDataPhysical(background, 64, 64, 12, 8);
        renderer.setPatternLookupData(lookup, 1);
        renderer.setClipHorizontal(true);
        Object nativeLookup = field(renderer, "patternLookup");
        Object nativeForegroundTexture = field(renderer, "foregroundTexture");
        var nativeState = renderer.captureBackgroundRenderState();
        int nativePendingBytes = renderer.getPendingBackgroundUploadBytes();
        for (boolean exceptional : new boolean[]{false, true}) {
            Runnable draw = () -> {
                assertNotSame(nativeLookup, fieldUnchecked(renderer, "patternLookup"));
                assertNotSame(nativeForegroundTexture, fieldUnchecked(renderer, "foregroundTexture"));
                assertNull(fieldUnchecked(renderer, "lookupData"));
                assertNull(fieldUnchecked(renderer, "foregroundData"));
                renderer.setTilemapData(TilemapGpuRenderer.Layer.FOREGROUND, new byte[64 * 64 * 4], 64, 64);
                renderer.setBackgroundTilemapDataPhysical(new byte[128 * 32 * 4], 128, 32, 0, 0);
                renderer.setPatternLookupData(new byte[]{4, 5, 6, (byte) 255}, 1);
                renderer.setClipHorizontal(false);
                if (exceptional) throw new IllegalStateException("aborted private image");
            };
            var residency = new CinematicBackgroundResidency();
            if (exceptional) assertThrows(IllegalStateException.class,
                    () -> renderer.withCinematicBackground(residency, draw));
            else renderer.withCinematicBackground(residency, draw);
            assertSame(nativeLookup, field(renderer, "patternLookup"));
            assertSame(nativeForegroundTexture, field(renderer, "foregroundTexture"));
            assertSame(foreground, field(renderer, "foregroundData"));
            assertEquals(128, field(renderer, "foregroundWidthTiles"));
            assertEquals(32, field(renderer, "foregroundHeightTiles"));
            assertEquals(true, field(renderer, "foregroundDirty"));
            assertSame(lookup, field(renderer, "lookupData"));
            assertEquals(1, field(renderer, "lookupSize"));
            assertEquals(true, field(renderer, "lookupDirty"));
            assertEquals(true, field(renderer, "clipHorizontal"));
            assertTrue(renderer.hasBackgroundBaseline(background, 64, 64));
            assertEquals(nativeState, renderer.captureBackgroundRenderState());
            assertEquals(nativePendingBytes, renderer.getPendingBackgroundUploadBytes());
        }
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Object fieldUnchecked(Object target, String name) {
        try { return field(target, name); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    @Test void fractionalColumnOriginGetsOneExtraEntryAndRestoresNativeCapacity() {
        var renderer = new TilemapGpuRenderer(350);
        assertEquals(22, renderer.getVScrollColumnCapacity());
        renderer.setCinematicColumnPhase(14);
        assertEquals(23, renderer.getVScrollColumnCapacity());
        renderer.setCinematicColumnPhase(14);
        assertEquals(23, renderer.getVScrollColumnCapacity());
        renderer.setCinematicColumnPhase(0);
        assertEquals(22, renderer.getVScrollColumnCapacity());
    }
}

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
